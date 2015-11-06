package edu.jhu.cvrg.waveform.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.PrincipalThreadLocal;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.security.permission.PermissionThreadLocal;
import com.liferay.portal.service.UserLocalServiceUtil;

import edu.jhu.cvrg.analysis.vo.AnalysisResultType;
import edu.jhu.cvrg.analysis.vo.AnalysisType;
import edu.jhu.cvrg.analysis.vo.AnalysisVO;
import edu.jhu.cvrg.data.dto.AnnotationDTO;
import edu.jhu.cvrg.data.dto.DocumentRecordDTO;
import edu.jhu.cvrg.data.factory.Connection;
import edu.jhu.cvrg.data.util.DataStorageException;
import edu.jhu.cvrg.filestore.main.FileStorer;
import edu.jhu.cvrg.filestore.model.FSFile;
import edu.jhu.cvrg.waveform.exception.AnalyzeFailureException;
import edu.jhu.cvrg.waveform.utility.ECGAnalyzeProcessor;
import edu.jhu.cvrg.waveform.utility.ServerUtility;
import edu.jhu.cvrg.waveform.utility.WebServiceUtility;

public class AnalysisThread extends Thread{

	private Map<String, Object> map;
	private Connection dbUtility;
	private DocumentRecordDTO documentRecord;
	
	private boolean hasChesnokovOutput = false;
	
	private long userId;
	private Long jobId = null;
	
	private String targetExtension;
	private Logger log = Logger.getLogger(AnalysisThread.class);
	
	private Map<String, Map<String, String>> ontologyCache;
	private boolean done = false;
	private String errorMessage;
	private FileStorer fileStorer = null;
	private AnalysisResultType resultType;
	
	/** Same as the other constructor, plus it has a threadGroup parameter
	 * 
	 * @param params
	 * @param documentRecordId
	 * @param hasWfdbAnnotationOutput
	 * @param originFiles
	 * @param userId
	 * @param dbUtility
	 * @param threadGroup
	 * @param args 
	 */
	
	public AnalysisThread(Map<String, Object> params, DocumentRecordDTO documentRecord, long userId, Connection dbUtility, ThreadGroup threadGroup, FileStorer fStorer, AnalysisResultType resultType) {
		super(threadGroup, (String)params.get("jobID"));
		this.jobId = Long.valueOf(((String) params.get("jobID")).replaceAll("\\D", ""));
		this.dbUtility = dbUtility;
		this.map = params;
		this.documentRecord = documentRecord;
		if("chesnokovWrapperType2".equals(map.get("method"))){
			hasChesnokovOutput = true;
		} 
		this.resultType = resultType;
		this.fileStorer = fStorer;
		this.userId = userId;
	}
	
	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
		AnalysisVO analysis = null;
		try{
			if((Double)map.get("durationSec") <= 1){
				log.error("Duration of " + (String)map.get("subjectID") + " is " + (Double)map.get("durationSec") + " seconds, but must be > 1.0");
				// throw new AnalyzeFailureException("Duration of " + (String)map.get("subjectID") + " is " + (Double)map.get("durationSec") + " seconds, but must be > 1.0");
			}
			
			Collection<Long> fileIds = null;
			this.initializeLiferayPermissionChecker(userId);
			analysis = new AnalysisVO(map.get("jobID").toString(), AnalysisType.getTypeByOmeName(map.get("method").toString()), resultType, null, map);
			analysis.setRecordName(map.get("subjectID").toString());
			
			boolean executeInPortlet = "Portlet".equals(map.get("openTsdbStrategy"));
			
			if(executeInPortlet && analysis.getType() != null){
				Map<Long, String> params = ECGAnalyzeProcessor.execute(documentRecord.getLeadCount(), documentRecord.getLeadNames(), documentRecord.getAduGain(), documentRecord.getSamplesPerChannel(), Double.valueOf(documentRecord.getSamplingRate()).floatValue(), documentRecord.getTimeSeriesId(), map, fileStorer, analysis);
				fileIds = params.keySet();
			}else{
				OMElement jobResult = WebServiceUtility.callWebServiceComplexParam(map,(String)map.get("method"),(String)map.get("serviceName"), (String)map.get("URL"), null, null);
				Map<String, OMElement> params = WebServiceUtility.extractParams(jobResult);
				
				if(params == null || params.size() <= 0 || params.get("error") != null){
					String errorMessage = (String) params.get("error").getText();
					if (errorMessage.length()>300) {
						log.info("errorMessage: " + errorMessage);
						log.info("errorMessage.length(): " + errorMessage.length());
						errorMessage = errorMessage.substring(0, 295) + "...";
						log.info("errorMessage: " + errorMessage);
					}
					throw new AnalyzeFailureException(errorMessage);
					
				}else{
					
					if(params.get("outputData") != null){
						analysis.setOutputData(((OMElement) params.get("outputData")).getText());
					}
					
					
					int fileCount = 0;
					if(params.get("filecount")!= null){
						fileCount = Integer.valueOf(((OMElement) params.get("filecount")).getText());
		
						OMElement fileList = params.get("fileList");
						if(fileCount>0 && (params.get("fileList")!= null)) {
							
							fileIds = new ArrayList<Long>();
							
							for (Iterator<OMElement> iterator = fileList.getChildElements(); iterator.hasNext();) {
								OMElement file = iterator.next();
								
								String fileIdStr = AnalysisThread.getElementByName(file, "fileId").getText();
								
								Long fileId = null; 
								if(fileIdStr != null && fileIdStr.length() > 0){
									fileId = Long.valueOf(fileIdStr);
									fileIds.add(fileId);
								}
							}
							
						}else{
							throw new AnalyzeFailureException("The parameter \"fileList\" is missing from web service (" + (String)map.get("method") + " response. There should be " + fileCount + " file names.");
						}
					}else{
						throw new AnalyzeFailureException("The parameter \"filecount\" is missing from web service (" + (String)map.get("method") + " response.");
					}
				}
			}
			
			if(fileIds != null && !fileIds.isEmpty()){
				recordAnalysisResults(documentRecord.getDocumentRecordId(), fileIds, analysis, documentRecord.getLeadNames());
			}else{
				throw new AnalyzeFailureException("The number of no result files received from web service (" + (String)map.get("method"));
			}
			dbUtility.updateAnalysisStatus(jobId, System.currentTimeMillis() - startTime, null);
		}catch (AnalyzeFailureException e){
			errorMessage = "Analysis (File - "+ analysis.getRecordName() +" | Algortithm " + analysis.getType() + " | FORMAT - " + analysis.getResultType() + ") returned the following error: \"" + e.getMessage() + "\"";
			log.error(errorMessage);
			ServerUtility.logStackTrace(e, log);
			if(jobId != null){
				try {
					dbUtility.updateAnalysisStatus(jobId, null, errorMessage);
				} catch (DataStorageException e1) {
					log.error(e1.getMessage());
					e1.printStackTrace();
				}
			}
		}catch (Exception e){
			errorMessage = "Analysis (File - "+ analysis.getRecordName() +" | Algortithm " + analysis.getType() + " | FORMAT - " + analysis.getResultType() + ") returned the following error: \"Fatal error. " + e.getMessage() + "\"";
			log.fatal(errorMessage);
			ServerUtility.logStackTrace(e, log);
			if(jobId != null){
				try {
					dbUtility.updateAnalysisStatus(jobId, null, errorMessage);
				} catch (DataStorageException e1) {
					log.error(e1.getMessage());
					e1.printStackTrace();
				}
			}
		}finally{
			if(analysis != null){
				this.deleteTempFolder(analysis);
			}
		}
	
		done = true;
	}
	
	public static OMElement getElementByName(OMElement parent, String tagName){
		return parent.getFirstChildWithName(new QName(parent.getNamespace().getNamespaceURI(), tagName, parent.getNamespace().getPrefix()));
	}
	
	private void recordAnalysisResults(Long documentRecordId, Collection<Long> filesId, AnalysisVO analysis, String leadNames) throws AnalyzeFailureException {
		
		if(filesId != null){
			boolean status = false;
			
			try {
				status = dbUtility.storeFilesInfo(documentRecordId, filesId, jobId);
			} catch (DataStorageException e) {
				status = false;
			}
			
			if(!status){
				throw new AnalyzeFailureException("Failure on FileInfo database persistence");
			}
		}
		
		if(analysis.getOutputData() == null && (analysis.getOutputFileNames() == null || analysis.getOutputFileNames().isEmpty())){
			this.createTempFiles(jobId, filesId, analysis);	
		}
		
		switch(analysis.getType()){
			case CHESNOKOV:
				this.storeChesnokovResults(documentRecordId, analysis, leadNames);
				break;
			case SQRS:
			case WQRS:
				
				try {
					this.storeAnnotationList(documentRecordId, analysis);
				} catch (AnalyzeFailureException e){
					ServerUtility.logStackTrace(e, log);
					throw e;
				} catch (JSONException e) {
					ServerUtility.logStackTrace(e, log);
					throw new AnalyzeFailureException("JSON error on annotation data extraction. [ERROR = "+e.getMessage()+" ]", e);
				} catch (Exception e) {
					ServerUtility.logStackTrace(e, log);
					throw new AnalyzeFailureException("Fail on annotation data extraction. [ERROR = "+e.getMessage()+" ]", e);
				}
				
				break;
			default:
				break;
		}
	}

	
	private void initializeLiferayPermissionChecker(long userId) throws AnalyzeFailureException {
		try{
			PrincipalThreadLocal.setName(userId);
			User user = UserLocalServiceUtil.getUserById(userId);
	        PermissionChecker permissionChecker = PermissionCheckerFactoryUtil.create(user);
	        PermissionThreadLocal.setPermissionChecker(permissionChecker);
		}catch (Exception e){
			ServerUtility.logStackTrace(e, log);
			throw new AnalyzeFailureException("Fail on premission checker initialization. [userId="+userId+"]", e);
		}
		
	}

	private void createTempFiles(long jobId, Collection<Long> filesId, AnalysisVO analysis) throws AnalyzeFailureException {
		
		String tempPath = ECGAnalyzeProcessor.SERVER_TEMP_ANALYSIS_FOLDER + File.separator + analysis.getJobId() + File.separator;
		
		List<FSFile> files = new ArrayList<>();
		try{
			for (long fileId : filesId) {
				
				FSFile file = fileStorer.getFile(fileId, false);
				
				switch (analysis.getType()) {
					case CHESNOKOV:
						if(hasChesnokovOutput){
							files.add(file);
							targetExtension = file.getExtension();
						}		
						break;
					case SQRS:
					case WQRS:	
						if(AnalysisThread.isAnotationFile(file.getName())){
							files.add(file);
							targetExtension = file.getExtension();
						}	
						break;
					default:
						break;
				}
			}
		}catch (Exception e){
			ServerUtility.logStackTrace(e, log);
			throw new AnalyzeFailureException("Fail on analysis result file read.", e);
		}
		
		for (FSFile liferayFile : files) {
			
			File targetDirectory = new File(tempPath);
			
			String targetFileName = tempPath + liferayFile.getName();
			
			if(liferayFile.getExtension().equals(targetExtension)){
				String replacement = '.'+liferayFile.getExtension();
				String target = ("_"+jobId+replacement);
						
				targetFileName = targetFileName.replace(target, replacement);
				
				analysis.addOutputFileName(targetFileName);
			}
			
			
			File targetFile = new File(targetFileName);
			
			try {
				targetDirectory.mkdirs();
				
				InputStream fileToSave = liferayFile.getFileDataAsInputStream();
				
				OutputStream fOutStream = new FileOutputStream(targetFile);

				int read = 0;
				byte[] bytes = new byte[1024];

				while ((read = fileToSave.read(bytes)) != -1) {
					fOutStream.write(bytes, 0, read);
				}

				fileToSave.close();
				fOutStream.flush();
				fOutStream.close();
				
			} catch (Exception e) {
				ServerUtility.logStackTrace(e, log);
				throw new AnalyzeFailureException("Fail on temporary file creation.", e);
			}finally{
				log.info("File created? " + targetFile.exists());
			}
		}
		
	}
	
	/** Maps values from PhysioBank Annotation Codes to the corresponding Bioportal ECG ontology IDs.<BR>
	 * Based on the list at http://www.physionet.org/physiobank/annotations.shtml <BR>
	 * key - PhysioBank Annotation Codes<BR>
	 * value - 
	 **/
	public static final Map<String, String> physioBankToOntology = new HashMap<String, String>(){
		private static final long serialVersionUID = 3855391277386558047L;
		{
			put("N", "http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000023"); // Normal beat
			put("(", "http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000703"); // Waveform onset
			put(")", "http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000236"); // Waveform end
			put("p", "http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000293"); // Peak of P-wave
			put("t", "http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000589"); // Peak of T-wave
			put("u", "http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000604"); // Peak of U-wave
//			put("L", "#xxxxxx"); // Left bundle branch block beat
//			put("R", "#xxxxxx"); // Right bundle branch block beat
//			put("B", "#xxxxxx"); // Bundle branch block beat (unspecified)
//			put("A", "#xxxxxx"); // Atrial premature beat
	    }
	};

	/** Stores the annotations from the ArrayList of String arrays generated by execute_rdann().<BR>
	 * Data are stored in database using Connection.storeLeadAnnotationNode().
	 * 
	 * @param alistAnnotation - output of execute_rdann() or changePhysioBankToOntology()
	 * @param jobId 
	 * @return true if all stored successfully
	 * @throws JSONException 
	 */	 
	private void storeAnnotationList(long recordId, AnalysisVO analysis) throws AnalyzeFailureException, JSONException{
		//	 * Required values that need to be filled in are:
		//	 * 
		//	 * created by (x) - the source of this annotation (whether it came from an algorithm or was entered manually)
		//	 * concept label - the type of annotation as defined in the annotation's bioportal reference term
		//	 * annotation ID - a unique ID used for easy retrieval of the annotation in the database
		//	 * onset label - the bioportal reference term for the onset position.  This indicates the start point of an interval
		//	 * 					or the location of a single point
		//	 * onset y-coordinate - the y coordinate for that point on the ECG wave
		//	 * onset t-coordinate - the t coordinate for that point on the ECG wave.
		//	 * an "isInterval" boolean - for determining whether this is an interval (and thus needs an offset tag)
		//	 * Full text description - This is the "value" so to speak, and contains the full definition of the annotation type being used
		//	 * 
		//	 * Note:  If this is an interval, then an offset label, y-coordinate, and t-coordinate are required for that as well.
		
		if(ontologyCache == null){
			ontologyCache = new HashMap<String, Map<String, String>>();
		}
		
		switch (analysis.getResultType()) {
			case JSON_DATA:
			case ORIGINAL_FILE:
			
				JSONArray results = (JSONArray)new JSONObject(analysis.getOutputData()).get("results");
				
				Set<AnnotationDTO> toPersist = new HashSet<AnnotationDTO>();
				for (int i = 0; i < results.length(); i++) {
					JSONObject saAnnot = (JSONObject)results.get(i); 
		
					if( (saAnnot != null) & (saAnnot.length()>=6)){
						try {
							double dMilliSec = Double.parseDouble(saAnnot.getString("Seconds"))*1000;
							String conceptId = physioBankToOntology.get(saAnnot.getString("Type"));
							if(conceptId == null){
								saAnnot.getString("Type");
							}
							if(conceptId.startsWith("http:")){
								int iLeadIndex = Integer.parseInt(saAnnot.getString("Chan"));
								double dMicroVolt = lookupVoltage(dMilliSec,iLeadIndex);
								
								Map<String, String> saOntDetails = ontologyCache.get(conceptId);
								if(saOntDetails == null){
									saOntDetails = WebServiceUtility.lookupOntology(AnnotationDTO.ELECTROCARDIOGRAPHY_ONTOLOGY, conceptId, "definition", "prefLabel");
									ontologyCache.put(conceptId, saOntDetails);
								}
								 
								String termName = "Not found";
								String fullAnnotation = "Not found";
								
								if(saOntDetails != null){
									termName = saOntDetails.get("prefLabel");
									fullAnnotation = saOntDetails.get("definition");
								}
			
								AnnotationDTO annotationToInsert = new AnnotationDTO(userId, recordId, null/*createdBy*/, "ANNOTATION", termName, 
																					 conceptId != null ? AnnotationDTO.ELECTROCARDIOGRAPHY_ONTOLOGY : null , conceptId,
																					 null/*bioportalRef*/, iLeadIndex, null/*unitMeasurement*/, null/*description*/,fullAnnotation, Calendar.getInstance(), 
																					 dMilliSec, dMicroVolt, dMilliSec, dMicroVolt //start and end are the same than this is a single point annotation 
																					 );
				
								annotationToInsert.setAnalysisJobId(jobId);
								
								// Inserting save to XML database
								toPersist.add(annotationToInsert);
							}else{
								log.error("conceptId:" + conceptId + " is probably wrong. Check the method changePhysioBankToOntology().");
							}
						} catch (NumberFormatException e) {
							ServerUtility.logStackTrace(e, log);
							throw new AnalyzeFailureException("Error on annotation data read.[dMilliSec="+saAnnot.getString("Seconds")+" | iLeadIndex="+saAnnot.getString("Chan")+"]", e); 
						} catch (Exception e){
							ServerUtility.logStackTrace(e, log);
							throw new AnalyzeFailureException("Fail on annotation data extraction. [ERROR = "+e.getMessage()+" ]", e);
						}
					}else{
						log.error("-- ERROR bad annotatation");
					}
				}
				
				Long insertedQtd = -1L;
				try {
					insertedQtd = dbUtility.storeAnnotations(toPersist);
				} catch (DataStorageException e) {
					log.error("Error on Annotations persistence. " + e.getMessage());
				}
				
				if(toPersist.size() != insertedQtd) {
					log.error("Annotation did not save properly");
				}
				
				break;
				
			default:
				break;
		}
			
	}
	
	private void storeChesnokovResults(long recordId, AnalysisVO analysis, String leadNames) {
		
		Set<AnnotationDTO> toPersist = new HashSet<AnnotationDTO>();
		Long insertedQtd = 0L;
		try {
			switch (analysis.getResultType()) {
				case CSV_FILE:
				case ORIGINAL_FILE:
					List<String> signalNameList = Arrays.asList(leadNames.split(","));
					
					BufferedReader in = null;
					try {
						in = new BufferedReader(new FileReader(analysis.getOutputFileNames().get(0)));
						String line = null;
						
						String[] dataHeaders = null;
						
						while((line = in.readLine()) != null) {
			            	if(line.trim().length() > 0) {
			            		if(dataHeaders == null){
				            		log.info(line);
				            		dataHeaders = line.split(",");
			            		}else{
			            			String[] data = line.split(",");
			            			String leadName = null;
			            			int leadIndex = 0;
			            			for (int i = 2; i < data.length; i++) {
			            				if(leadName == null){
			            					leadName = data[i];
			            					for(int s=0;s<signalNameList.size();s++){
			            						if(signalNameList.get(s).equals(leadName)){
			            							leadIndex = s;
			            							break;
			            						}
			            					}
			            				}else{
			            					String[] annotationSubject = dataHeaders[i].split("\\|");
			    							String conceptId = annotationSubject[0].substring(annotationSubject[0].lastIndexOf("/")+1);
			    							
			    							AnnotationDTO annotationToInsert = new AnnotationDTO(userId, recordId, "ChesnokovAnalysis"/*createdBy*/, "ANNOTATION", annotationSubject[1], 
													 conceptId != null ? AnnotationDTO.ECG_TERMS_ONTOLOGY : null , conceptId,
													 annotationSubject[0]/*bioportalRef*/, leadIndex , null/*unitMeasurement*/, null/*description*/, data[i], Calendar.getInstance(), 
													 null, null, null, null //start and end are the same than this is a single point annotation 
													 );
			    							
			    							annotationToInsert.setAnalysisJobId(jobId);
			    							
			    							toPersist.add(annotationToInsert);
			
			            				}
			            			}
			            		}
			            	} 
			            }
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}finally{
						if(in !=  null){
							try {
								in.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							
						}
					}
					break;
		
				case JSON_DATA:
					break;
			}
			
			insertedQtd = dbUtility.storeAnnotations(toPersist);
			
		} catch (DataStorageException e) {
			log.error("Error on Chesnokov's Annotations persistence. " + e.getMessage());
		}finally{
			if(toPersist.size() != insertedQtd) {
				log.error("Annotation did not save properly");
			}
		}
	}

	/** Looks up the voltage value for the specified timestamp on the specified lead index.
	 * 
	 * @param dMilliSec
	 * @param iLeadIndex
	 * @return
	 */
	private double lookupVoltage(double dMilliSec,int iLeadIndex){
		//TODO implement this, for now zero will work because the dygraph annotation display code ignores the y axis value.
		return 0.0;		
	}
	
	private static boolean isAnotationFile(String fileName){
		String tmp = fileName.replaceAll("\\.(w?qrs|atr)\\d*$", "");
		return !tmp.equals(fileName);
	}
	
	private static boolean isHeaderFile(String fileName){
		String tmp = fileName.replaceAll("\\.hea$", "");
		return !tmp.equals(fileName);
	}
	
	public boolean hasError(){
		return errorMessage != null;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean isDone() {
		return done;
	}
	
	public void deleteTempFolder(AnalysisVO analysis){
		
		String tempPath = ECGAnalyzeProcessor.SERVER_TEMP_ANALYSIS_FOLDER + File.separator + analysis.getJobId() + File.separator;
		File tempDir = new File(tempPath);
		if(tempDir.isDirectory()){
			File[] contentFiles = tempDir.listFiles();
			for (File file : contentFiles) {
				file.delete();
			}
			tempDir.delete();
		}
	}

}
