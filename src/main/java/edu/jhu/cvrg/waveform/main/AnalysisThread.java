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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.log4j.Logger;

import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.PrincipalThreadLocal;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.security.permission.PermissionThreadLocal;
import com.liferay.portal.service.UserLocalServiceUtil;

import edu.jhu.cvrg.data.dto.AnnotationDTO;
import edu.jhu.cvrg.data.factory.Connection;
import edu.jhu.cvrg.data.util.DataStorageException;
import edu.jhu.cvrg.filestore.main.FileStoreFactory;
import edu.jhu.cvrg.filestore.main.FileStorer;
import edu.jhu.cvrg.filestore.model.FSFile;
import edu.jhu.cvrg.waveform.exception.AnalyzeFailureException;
import edu.jhu.cvrg.waveform.utility.ResourceUtility;
import edu.jhu.cvrg.waveform.utility.ServerUtility;
import edu.jhu.cvrg.waveform.utility.WebServiceUtility;

public class AnalysisThread extends Thread{

	private Map<String, Object> map;
	private Connection dbUtility;
	private long documentRecordId;
	
	private boolean hasWfdbAnnotationOutput;
	private boolean hasChesnokovOutput = false;
	
	private ArrayList<FSFile> originFiles;
	private long userId;
	private Long jobId = null;
	
	private String headerFileName;
	private String targetExtension;
	private String csvFileName;
	private Logger log = Logger.getLogger(AnalysisThread.class);
	
	private Map<String, Map<String, String>> ontologyCache;
	private boolean done = false;
	private String errorMessage;
	private FileStorer fileStorer = null;
	
	public AnalysisThread(Map<String, Object> params, long documentRecordId, boolean hasWfdbAnnotationOutput, ArrayList<FSFile> originFiles, long userId, Connection dbUtility) {
		super((String)params.get("jobID"));
		this.jobId = Long.valueOf(((String) params.get("jobID")).replaceAll("\\D", ""));
		this.dbUtility = dbUtility;
		this.map = params;
		this.documentRecordId = documentRecordId;
		this.hasWfdbAnnotationOutput = hasWfdbAnnotationOutput;
		this.originFiles = originFiles;
		this.userId = userId;
	}
	
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
	public AnalysisThread(Map<String, Object> params, long documentRecordId, boolean hasWfdbAnnotationOutput, ArrayList<FSFile> originFiles, long userId, Connection dbUtility, ThreadGroup threadGroup, String[] args) {
		super(threadGroup, (String)params.get("jobID"));
		this.jobId = Long.valueOf(((String) params.get("jobID")).replaceAll("\\D", ""));
		this.dbUtility = dbUtility;
		this.map = params;
		this.documentRecordId = documentRecordId;
		this.hasWfdbAnnotationOutput = hasWfdbAnnotationOutput;
		if("chesnokovWrapperType2".equals(map.get("method"))){
			hasChesnokovOutput = true;
		} 
		
		fileStorer = FileStoreFactory.returnFileStore(ResourceUtility.getFileStorageType(), args);
		this.originFiles = originFiles;
		this.userId = userId;
	}
	
	@Override
	public void run() {
		long startTime = System.currentTimeMillis();
		try{
			if((Double)map.get("durationSec") <= 1){
				log.error("Duration of " + (String)map.get("subjectID") + " is " + (Double)map.get("durationSec") + " seconds, but must be > 1.0");
				// throw new AnalyzeFailureException("Duration of " + (String)map.get("subjectID") + " is " + (Double)map.get("durationSec") + " seconds, but must be > 1.0");
			}
			OMElement jobResult = WebServiceUtility.callWebServiceComplexParam(map,(String)map.get("method"),(String)map.get("serviceName"), (String)map.get("URL"), null, null);
			
			Map<String, OMElement> params = WebServiceUtility.extractParams(jobResult);
			
			if(params != null && params.size() > 0){
				if(params.get("error")!= null){
					String errorMessage = (String) params.get("error").getText();
					if (errorMessage.length()>300) {
						log.info("errorMessage: " + errorMessage);
						log.info("errorMessage.length(): " + errorMessage.length());
						errorMessage = errorMessage.substring(0, 295) + "...";
						log.info("errorMessage: " + errorMessage);
					}
					throw new AnalyzeFailureException(errorMessage);
				}

				int fileCount = 0;
				if(params.get("filecount")!= null){
					fileCount = Integer.valueOf(params.get("filecount").getText());
	
					OMElement fileList = null;
					if(fileCount>0 && (params.get("fileList")!= null)) {
						fileList = params.get("fileList");
					
						long[] filesId = new long[fileCount];
						
						int i = 0;
						for (Iterator<OMElement> iterator = fileList.getChildElements(); iterator.hasNext();) {
							OMElement file = iterator.next();
							
							String fileIdStr = AnalysisThread.getElementByName(file, "FileId").getText();
							
							Long fileId = null; 
							if(fileIdStr != null && fileIdStr.length() > 0){
								fileId = Long.valueOf(fileIdStr);
								filesId[i] = fileId;
								i++;
							}
						}
						if(i==fileCount){
							recordAnalysisResults(documentRecordId, jobId, filesId);
						}else{
							throw new AnalyzeFailureException("The number of result files received (" + i + ") did not match the number expected (" + fileCount + ") from web service (" + (String)map.get("method"));
						}
						dbUtility.updateAnalysisStatus(jobId, System.currentTimeMillis() - startTime, null);
					}else{
						throw new AnalyzeFailureException("The parameter \"fileList\" is missing from web service (" + (String)map.get("method") + " response. There should be " + fileCount + " file names.");
					}
				}else{
					throw new AnalyzeFailureException("The parameter \"filecount\" is missing from web service (" + (String)map.get("method") + " response.");
				}
			}
		}catch (AnalyzeFailureException e){
			errorMessage = e.getMessage();
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
			errorMessage = "Fatal error. " + e.getMessage();
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
		}
	
		done = true;
	}
	
	public static OMElement getElementByName(OMElement parent, String tagName){
		return parent.getFirstChildWithName(new QName(parent.getNamespace().getNamespaceURI(), tagName, parent.getNamespace().getPrefix()));
	}
	
	private void recordAnalysisResults(Long documentRecordId, Long jobId, long[] filesId) throws AnalyzeFailureException {
		
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
		
		if(hasChesnokovOutput){
			
			this.initializeLiferayPermissionChecker(userId);
			this.createTempFiles(jobId, originFiles, filesId);
			this.storeChesnokovResults(csvFileName, documentRecordId, jobId);
			
		}else if(hasWfdbAnnotationOutput){
			if(ontologyCache == null){
				ontologyCache = new HashMap<String, Map<String, String>>();
			}
			
			try {
				
				this.initializeLiferayPermissionChecker(userId);
				
				this.createTempFiles(jobId, originFiles, filesId);
				List<String[]> result = execute_rdann(headerFileName, targetExtension);
				result = this.changePhysioBankToOntology(result);
				this.storeAnnotationList(result, documentRecordId, jobId);
			
			} catch (AnalyzeFailureException e){
				ServerUtility.logStackTrace(e, log);
				throw e;
			} catch (Exception e) {
				ServerUtility.logStackTrace(e, log);
				throw new AnalyzeFailureException("Fail on annotation data extraction. [ERROR = "+e.getMessage()+" ]", e);
			}
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

	/** Reads a WFDB annotation file and returns an ArrayList of String arrays containing the data from the WFDB annotation file.<BR>
	 * <BR>
	 * Below is an example of  first 2 1/2 seconds from wqrs run on twa01.hea,twa01.dat<BR>
	 * Command that makes the annotation file:  wqrs -r twa01 -v -j<BR>
	 * Command that parses the annotation file: rdann -x -v -r twa01 -a wqrs -t 2.5<BR>
	 array[0]     [1]        [2]    [3]  [4]  [5]  [6]      [7]		 <BR>
	  Seconds   Minutes     Hours  Type  Sub Chan  Num      Aux<BR>
	    0.194   0.00323 0.0000539     N    0    0    0<BR>
	    0.260   0.00433 0.0000722     )    0    0    0<BR>
	    0.752   0.01253 0.0002089     N    0    0    0<BR>
	    0.818   0.01363 0.0002272     )    0    0    0<BR>
	    1.288   0.02147 0.0003578     N    0    0    0<BR>
	    1.354   0.02257 0.0003761     )    0    0    0<BR>
	    1.820   0.03033 0.0005056     N    0    0    0<BR>
	    1.888   0.03147 0.0005244     )    0    0    0<BR>
	    2.362   0.03937 0.0006561     N    0    0    0<BR>
	    2.428   0.04047 0.0006744     )    0    0    0<BR>
	    @see  http://www.physionet.org/physiobank/annotations.shtml
	    @return In each String array the elements contain the following data:<BR>
	    @param headerFileName Header file name.
	    @param annotation is the suffix (extension) of the bare name of the annotation file
	 * [0] - Seconds, total time from the beginning of the file in decimal seconds, to 3 decimal places.<BR>
	 * [1] - Minutes, same value as Seconds above, but in units of Minutes, to 5 decimal places (e.g. Seconds/60.0)<BR>
	 * [2] - Hours, same value as Seconds above, but in units of Hours, to 7 decimal places (e.g. Seconds/3600.0)<BR>
	 * [3] - Type, PhysioBank Annotation Code, <BR>
	 * [4] - Sub, Subtype - context-dependent attribute (see the documentation for each Physionet database for details), it's always been zero on our sample data.<BR>
	 * [5] - Chan, Channel(lead) number, zero base integer. (e.g. 0 = leadI, 5 = leadV1)<BR>
	 * [6] - Num, context-dependent attribute (see the documentation for each Physionet database for details), it's always been zero on our sample data.<BR>
	 * [7] - Aux, a free text string, it's always been blank on our sample data.<BR>
	**/
	private List<String[]> execute_rdann(String headerFileName, String annotation) throws AnalyzeFailureException{
		
		List<String[]> alistAnnotation = new ArrayList<String[]>();
		
		String[] asEnvVar = new String[0];   
		int iIndexPeriod = headerFileName.lastIndexOf(".");
		String sRecord = headerFileName.substring(0, iIndexPeriod);
		 
		try { 
			ServerUtility util = new ServerUtility();
			// rdann -v -x -r twa01 -a wqrs
			String sCommand = "rdann -x -r " + sRecord + " -a " + annotation;
			util.executeCommand(sCommand, asEnvVar, "/");
			
			String line;
			int lineNum = 0;
		    String[] columns;
		    
		    //iterate thru the returned text of the command, one line per annotation.");
		    while ((line = util.stdInputBuffer.readLine()) != null) {
		    	// columns: Seconds   Minutes     Hours  Type  Sub Chan  Num      Aux
		    	line = line.trim();
				columns = line.split("\\s+");
				alistAnnotation.add(columns);
				lineNum++;
			}
			log.info("--- execute_rdann() found " + lineNum + " annotations");
		} catch (IOException e) {
			ServerUtility.logStackTrace(e, log);
			throw new AnalyzeFailureException("Fail on RDANN execution. [recordname="+sRecord+"]", e);
		}finally{
			File jobFolder = new File(headerFileName).getParentFile();
			File[] files = jobFolder.listFiles();
			
			for (File file : files) {
				file.delete();
			}
			
			jobFolder.delete();
		}
		return alistAnnotation;
	}
	
	private void createTempFiles(long jobId, List<FSFile> wfdbFiles, long[] filesId ) throws AnalyzeFailureException {
		
		String tempPath = System.getProperty("java.io.tmpdir") + File.separator + "waveform/a" + File.separator + jobId + File.separator;
		
		try{
			for (long fileId : filesId) {
				
				FSFile file = fileStorer.getFile(fileId, false);
				
				if(hasWfdbAnnotationOutput && AnalysisThread.isAnotationFile(file.getName())){
					wfdbFiles.add(file);
					targetExtension = file.getExtension();
					break;
				}
				if(hasChesnokovOutput){
					wfdbFiles.add(file);
					targetExtension = file.getExtension();
					break;
				}
			}
		}catch (Exception e){
			ServerUtility.logStackTrace(e, log);
			throw new AnalyzeFailureException("Fail on analysis result file read.", e);
		}
		
		for (FSFile liferayFile : wfdbFiles) {
			
			File targetDirectory = new File(tempPath);
			
			String targetFileName = tempPath + liferayFile.getName();
			
			if(liferayFile.getExtension().equals(targetExtension)){
				String replacement = '.'+liferayFile.getExtension();
				String target = ("_"+jobId+replacement);
						
				targetFileName = targetFileName.replace(target, replacement);
				
				if(hasChesnokovOutput){
					csvFileName = targetFileName;
				}
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
			
			if(AnalysisThread.isHeaderFile(liferayFile.getName())){
				headerFileName = targetFile.getAbsolutePath();
			}
		}
		
	}
	
	/** STUB METHOD<BR>
	 * Change all of the values from PhysioBank Annotation Codes to the corresponding Bioportal ECG ontology IDs.<BR>
	 * Based on the list at http://www.physionet.org/physiobank/annotations.shtml <BR>
	 * 
	 * @param annotationMap - the original annotations with Key =  time stamp, and Value = PhysioBank Annotation Codes
	 * @return - same as the passed in LinkedHashMap but with the Values as Bioportal ECG ontology IDs.
	 */
	private List<String[]> changePhysioBankToOntology(List<String[]>  alistAnnotation){
		// TODO STUB METHOD-Needs to be fully implemented.
		log.info("- changePhysioBankToOntology() alistAnnotation.size():" + alistAnnotation.size());
		String sPhysioBankCode="";
		for(String[] saAnnot : alistAnnotation){
			sPhysioBankCode = saAnnot[3];
			String ontID = physioBankToOntology.get(sPhysioBankCode);
			if(ontID != null){
				saAnnot[3] = ontID;
			}
		}
		return alistAnnotation;
	}
	
	/** Maps values from PhysioBank Annotation Codes to the corresponding Bioportal ECG ontology IDs.<BR>
	 * Based on the list at http://www.physionet.org/physiobank/annotations.shtml <BR>
	 * key - PhysioBank Annotation Codes<BR>
	 * value - 
	 **/
	public static final Map<String, String> physioBankToOntology = new HashMap<String, String>(){
		private static final long serialVersionUID = 3855391277386558047L;
		{
			put("N", "ECGTermsv1.owl#ECG_000000023"); // Normal beat
			put("(", "ECGTermsv1.owl#ECG_000000703"); // Waveform onset
			put(")", "ECGTermsv1.owl#ECG_000000236"); // Waveform end
			put("p", "ECGTermsv1.owl#ECG_000000293"); // Peak of P-wave
			put("t", "ECGTermsv1.owl#ECG_000000589"); // Peak of T-wave
			put("u", "ECGTermsv1.owl#ECG_000000604"); // Peak of U-wave
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
	 */	 
	private void storeAnnotationList(List<String[]> alistAnnotation, long recordId, Long jobId) throws AnalyzeFailureException{
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
		
		Set<AnnotationDTO> toPersist = new HashSet<AnnotationDTO>();
		for(String[] saAnnot : alistAnnotation){

			if( (saAnnot != null) & (saAnnot.length>=6)){
				try {
					double dMilliSec = Double.parseDouble(saAnnot[0])*1000;
					String conceptId = saAnnot[3];
					if(conceptId.startsWith("http:")){
						int iLeadIndex = Integer.parseInt(saAnnot[5]);
						double dMicroVolt = lookupVoltage(dMilliSec,iLeadIndex);
						
						Map<String, String> saOntDetails = ontologyCache.get(conceptId);
						if(saOntDetails == null){
							saOntDetails = WebServiceUtility.lookupOntology(AnnotationDTO.ECG_TERMS_ONTOLOGY, conceptId, "definition", "prefLabel");
							ontologyCache.put(conceptId, saOntDetails);
						}
						 
						String termName = "Not found";
						String fullAnnotation = "Not found";
						
						if(saOntDetails != null){
							termName = saOntDetails.get("prefLabel");
							fullAnnotation = saOntDetails.get("definition");
						}
	
						AnnotationDTO annotationToInsert = new AnnotationDTO(0L/*userid*/, recordId, null/*createdBy*/, "ANNOTATION", termName, 
																			 conceptId != null ? AnnotationDTO.ECG_TERMS_ONTOLOGY : null , conceptId,
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
					throw new AnalyzeFailureException("Error on annotation data read.[dMilliSec="+saAnnot[0]+" | iLeadIndex="+saAnnot[5]+"]", e); 
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
		
	}
	
	private void storeChesnokovResults(String csvFile, long recordId, long jobId) {
		
		List<String> signalNameList = populateSignalNameList(csvFile, new String[0]);
		
		Set<AnnotationDTO> toPersist = new HashSet<AnnotationDTO>();
		
		BufferedReader in = null;
		Long insertedQtd = 0L;
		try {
			in = new BufferedReader(new FileReader(csvFile));
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
    							
    							AnnotationDTO annotationToInsert = new AnnotationDTO(0L/*userid*/, recordId, "ChesnokovAnalysis"/*createdBy*/, "ANNOTATION", annotationSubject[1], 
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
			
			insertedQtd = dbUtility.storeAnnotations(toPersist);
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DataStorageException e) {
			log.error("Error on Chesnokov's Annotations persistence. " + e.getMessage());
		}finally{
			if(in !=  null){
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
			if(toPersist.size() != insertedQtd) {
				log.error("Annotation did not save properly");
			}
		}
	}

	/** populates the variable signalNameList using the Physionet library program "signame"
	 * 
	 * @param sInputFile
	 * @param asEnvVar
	 */
	private List<String> populateSignalNameList(String sInputFile, String[] asEnvVar){
		
		List<String> signalNameList = new ArrayList<String>();
		try{ 
			String recordName = sInputFile.substring(0, sInputFile.lastIndexOf("."));
			String command = "signame -r " + recordName;
			ServerUtility util = new ServerUtility();
			util.executeCommand(command, asEnvVar, "/");
			
			String tempLine = "";
			int lineNumber=0;
			
			while ((tempLine = util.stdInputBuffer.readLine()) != null) {
		    	if (lineNumber<12){
		    		log.info("signame(); " + lineNumber + ")" + tempLine);
		    	}
		    	signalNameList.add(tempLine);
		    	lineNumber++;
		    }
		
		} catch (IOException ioe) {
			log.error("IOException Message: rdsamp " + ioe.getMessage());
			ioe.printStackTrace();
		} catch (Exception e) {
			System.err.println("Exception Message: rdsamp " + e.getMessage());
			e.printStackTrace();
		}
		
		return signalNameList;
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

}
