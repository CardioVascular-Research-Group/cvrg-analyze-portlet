package edu.jhu.cvrg.waveform.main;
/*
Copyright 2013 Johns Hopkins University Institute for Computational Medicine

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
/**
* @author Michael Shipway, Chris Jurado, Stephen Granite
* 
*/
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;
import javax.portlet.PortletSession;

import org.apache.axiom.om.OMElement;

import edu.jhu.cvrg.data.dto.AlgorithmDTO;
import edu.jhu.cvrg.data.dto.AnalysisJobDTO;
import edu.jhu.cvrg.data.dto.AnalysisStatusDTO;
import edu.jhu.cvrg.data.dto.AnnotationDTO;
import edu.jhu.cvrg.data.dto.FileInfoDTO;
import edu.jhu.cvrg.data.enums.FileType;
import edu.jhu.cvrg.data.factory.Connection;
import edu.jhu.cvrg.data.factory.ConnectionFactory;
import edu.jhu.cvrg.data.util.DataStorageException;
import edu.jhu.cvrg.filestore.exception.FSException;
import edu.jhu.cvrg.filestore.main.FileStoreFactory;
import edu.jhu.cvrg.filestore.main.FileStorer;
import edu.jhu.cvrg.filestore.model.FSFile;
import edu.jhu.cvrg.waveform.model.DocumentDragVO;
import edu.jhu.cvrg.waveform.utility.ResourceUtility;
import edu.jhu.cvrg.waveform.utility.ThreadController;
import edu.jhu.cvrg.waveform.utility.WebServiceUtility;
//import edu.jhu.cvrg.waveform.model.Algorithm;

public class AnalysisManager implements Serializable{

	private static final long serialVersionUID = -6155747608247379918L;
	
	private ThreadController tController;
	
	public boolean performAnalysis(List<DocumentDragVO> selectedNodes, long userId, AlgorithmDTO[] selectedAlgorithms ){
		
		try {
			Connection dbUtility = ConnectionFactory.createConnection();
			
			Set<AnalysisThread> threadSet = new HashSet<AnalysisThread>();
			
			ThreadGroup analysisGroup = ThreadController.createSubGroup("AnalysisGroup");
			
			String[] args = {String.valueOf(ResourceUtility.getCurrentGroupId()), String.valueOf(ResourceUtility.getCurrentUserId()), String.valueOf(ResourceUtility.getCurrentCompanyId())};
			FileStorer fileStorer = FileStoreFactory.returnFileStore(ResourceUtility.getFileStorageType(), args);
			
			for (DocumentDragVO node : selectedNodes) {
				
				List<FileInfoDTO> files = dbUtility.getAllFilesByDocumentRecordId(node.getDocumentRecord().getDocumentRecordId());
				FSFile headerFile = fileStorer.getFile(files.get(0).getFileEntryId(), true);
				
				FileType originalFileType = node.getDocumentRecord().getOriginalFormat();
				long docId = node.getDocumentRecord().getDocumentRecordId();
				String timeseriesId = node.getDocumentRecord().getTimeSeriesId();
				String name = node.getDocumentRecord().getRecordName();
				String age = node.getDocumentRecord().getAge().toString();
				String sex = node.getDocumentRecord().getGender();
				String channels = String.valueOf(node.getDocumentRecord().getLeadCount());
				String scalingFactor = String.valueOf(node.getDocumentRecord().getAduGain());
				String samplesPerChannel = String.valueOf(node.getDocumentRecord().getSamplesPerChannel());
				String samplingRate = String.valueOf(node.getDocumentRecord().getSamplingRate());
				
				ThreadGroup fileGroup = new ThreadGroup(analysisGroup, docId + "Group");
				List<Long> analysisIds = new ArrayList<Long>();
				
				for (AlgorithmDTO algorithm : selectedAlgorithms) {
					
					Map<String, Object> parameterMap = new HashMap<String, Object>();
					
					parameterMap.put("userID",  String.valueOf(userId));
					parameterMap.put("groupID", String.valueOf(ResourceUtility.getCurrentGroupId()));
					parameterMap.put("folderID", String.valueOf(headerFile.getParentId()));
					parameterMap.put("subjectID", node.getDocumentRecord().getSubjectId());
					parameterMap.put("durationSec",node.getDocumentRecord().getDurationSec());
					parameterMap.put("channels", channels);
					parameterMap.put("leadNames", node.getDocumentRecord().getLeadNames());
					parameterMap.put("scalingFactor", scalingFactor);
					parameterMap.put("samplesPerChannel", samplesPerChannel);
					parameterMap.put("samplingRate", samplingRate);
					parameterMap.put("timeseriesId", timeseriesId);
					
					LinkedHashMap<String, String> parameterlistMap = new LinkedHashMap<String, String>();					
					if( (originalFileType.getLabel().equals("Schiller")) && (algorithm.getDisplayShortName().contentEquals("QRS-Score")) ){
						 List<AnnotationDTO> magellanAnnot = getMagellanLeadAnnotationList(userId, docId, "Schiller Upload", "ECGT");
						 List<AnnotationDTO> magellanRecAnnot = getMagellanRecordAnnotationList(userId, docId, "Schiller Upload", "ECGT");
						 String qrsd=null, qrsax=null;
						 
						 if(magellanRecAnnot.get(0) != null){
							 qrsd = magellanRecAnnot.get(0).getValue();
						 }
						 if(magellanRecAnnot.get(1) != null){
							 qrsax = magellanRecAnnot.get(1).getValue();
						 }
						 parameterlistMap = buildQRS_ScoreParameterListMap(docId, name, age, sex, qrsd, qrsax, magellanAnnot);
					}
					
					parameterMap.put("parameterlist", parameterlistMap);
					parameterMap.put("method", algorithm.getServiceMethod());
					parameterMap.put("serviceName", algorithm.getServiceName());
					parameterMap.put("URL", algorithm.getAnalysisServiceURL());
					parameterMap.put("openTsdbHost", ResourceUtility.getOpenTsdbHost());
					parameterMap.put("openTsdbStrategy", ResourceUtility.getOpenTsdStrategy());
					
					AnalysisJobDTO analysisJobDTO = dbUtility.storeAnalysisJob(node.getFileNode().getDocumentRecordId(), 0, 0, algorithm.getAnalysisServiceURL(), algorithm.getServiceName(), algorithm.getServiceMethod(), new Date(), ResourceUtility.getCurrentUserId());
					
					String jobID = "job_" + analysisJobDTO.getAnalysisJobId();
					
					parameterMap.put("jobID", jobID);
					
					AnalysisThread t = new AnalysisThread(parameterMap, node.getDocumentRecord(), ResourceUtility.getCurrentUserId(), dbUtility, fileGroup, fileStorer, algorithm.getResultType());
					
					threadSet.add(t);
					
					analysisIds.add(analysisJobDTO.getAnalysisJobId());
				}
				AnalysisStatusDTO dto =  new AnalysisStatusDTO(node.getDocumentRecord().getDocumentRecordId(), node.getDocumentRecord().getRecordName(), analysisIds.size(), 0, 0);
				dto.setAnalysisIds(analysisIds);
				this.addToBackgroundQueue(dto);
			}
			
			tController = new ThreadController(threadSet);
			tController.start();
			
			return true;
		} catch (FSException e) {
			e.printStackTrace();
		} catch (DataStorageException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	private ArrayList<FSFile> getFileList(AlgorithmDTO algorithm, List<FSFile> subFiles) {
		ArrayList<FSFile> retFiles = new ArrayList<FSFile>();
		String needExtentions = "";
		if(algorithm.getType() == null){
			needExtentions = ".hea.dat.xyz";
		}else{
			switch (algorithm.getType()) {
				case ANN2RR:
				case NGUESS:
				case PNNLIST:
				case TACH:
					needExtentions = ".atr.qrs.wqrs.hea.dat.xyz"; 
					break;
				case SQRS:
				case WQRS:
				case RDSAMP:
				case SIGAAMP:
				case CHESNOKOV:
				case SQRS4IHR:
				case WQRS4IHR:
				case SQRS4PNNLIST:
				case WQRS4PNNLIST:
					needExtentions = ".hea.dat.xyz"; 
					break;
				case WRSAMP:
					needExtentions = ".txt"; 
					break;
				default: 
					needExtentions = ".hea.dat.xyz";
					break;
			}
		}
		
		for (FSFile file : subFiles) {
			if(needExtentions.contains(file.getExtension())){
				retFiles.add(file);
			}
		}
	
		return retFiles;
	}

	private String retrievePrimaryData(String chesnokovSubjectIds, String chesnokovFiles, String uId) {

		OMElement omeResult;

		LinkedHashMap<String, String> parameterMap = new LinkedHashMap<String, String>();

		parameterMap.put("userid", uId);
		parameterMap.put("chesSubjectids", chesnokovSubjectIds);
		parameterMap.put("chesFiles", chesnokovFiles);
		parameterMap.put("service", ResourceUtility.getDataTransferClass());
		parameterMap.put("logindatetime", new Long(System.currentTimeMillis()).toString());
		
		omeResult = WebServiceUtility.callWebService(parameterMap, ResourceUtility.getConsolidatePrimaryAndDerivedDataMethod(), ResourceUtility.getNodeDataServiceName(), null);
		return omeResult.getText();
	}	

	public int getTotal() {
		return tController.getThreadCount();
	}

	
	public int getDone() {
		int done = 0;
		if(tController != null){
			Collection<AnalysisThread> tCollection = (Collection<AnalysisThread>) tController.getThreads();
			for (AnalysisThread aThread : tCollection) {
				if(aThread.isDone()){
					done++;
				}
			}
		}
		return done;
	}
	
	public List<String> getMessages() {
		List<String> messages = null;
		if(tController != null){
			Collection<AnalysisThread> tCollection = (Collection<AnalysisThread>) tController.getThreads();
			for (AnalysisThread t : tCollection) {
				if(!t.isAlive()){
					if(t.hasError()){
						if(messages == null){
							messages = new ArrayList<String>();
						}
						
						messages.add(t.getErrorMessage());
					}
				}
			}
		}
		return messages;
	}

	/** Gets an ArrayList of lead annotations, on all the standard 12 leads for Q_Wave_Amplitude, Q_Wave_Duration, R_Wave_Amplitude, R_Wave_Duration, S_Wave_Amplitude;
	 *  which are found in the Magellan text output file.
	 * 
	 * @param userId - Id of the user who owns this data.
	 * @param docId - Document ID
	 * @param createdBy - Either original file format identifier, algorithm identifier, or user ID in the case of manual annotations.
	 * @param bioportalOntologyID - Identifier of the Ontology, e.g. "ECGT"
	 **/
	private List<AnnotationDTO> getMagellanLeadAnnotationList(long userId, long docId, String createdBy, String bioportalOntologyID){
		List<AnnotationDTO> retList= new ArrayList<AnnotationDTO>();
		
		List <String> bioportalClassIdList = new ArrayList<String>();
		bioportalClassIdList.add("http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000652"); // Q_Wave_Amplitude ECGOntology:ECG_000000652
		bioportalClassIdList.add("http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000551"); // Q_Wave_Duration ECGOntology:ECG_000000551
		bioportalClassIdList.add("http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000750"); // R_Wave_Amplitude ECGOntology:ECG_000000750
		bioportalClassIdList.add("http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000597"); // R_Wave_Duration ECGOntology:ECG_000000597
		bioportalClassIdList.add("http://www.cvrgrid.org/files/ECGOntologyv1.owl#ECG_000000107"); // S_Wave_Amplitude ECGOntology:ECG_000000107
//		bioportalClassIdList.add("ECGOntology:ECG_000000072"); // QRS_Wave_Duration
		
		try {
			Connection conn = ConnectionFactory.createConnection();
			List<AnnotationDTO> paramList_I = conn.getLeadAnnotationListConceptIDList(userId, docId, 0, createdBy, bioportalOntologyID, bioportalClassIdList);
			List<AnnotationDTO> paramList_II = conn.getLeadAnnotationListConceptIDList(userId, docId, 1, createdBy, bioportalOntologyID, bioportalClassIdList);
//			List<AnnotationDTO> paramList_III = conn.getLeadAnnotationListConceptIDList(userId, docId, 2, createdBy, bioportalOntologyID, bioportalClassIdList);
//			List<AnnotationDTO> paramList_aVR = conn.getLeadAnnotationListConceptIDList(userId, docId, 3, createdBy, bioportalOntologyID, bioportalClassIdList);
			List<AnnotationDTO> paramList_aVL = conn.getLeadAnnotationListConceptIDList(userId, docId, 4, createdBy, bioportalOntologyID, bioportalClassIdList);
			List<AnnotationDTO> paramList_aVF = conn.getLeadAnnotationListConceptIDList(userId, docId, 5, createdBy, bioportalOntologyID, bioportalClassIdList);
			List<AnnotationDTO> paramList_V1 = conn.getLeadAnnotationListConceptIDList(userId, docId, 6, createdBy, bioportalOntologyID, bioportalClassIdList);
			List<AnnotationDTO> paramList_V2 = conn.getLeadAnnotationListConceptIDList(userId, docId, 7, createdBy, bioportalOntologyID, bioportalClassIdList);
			List<AnnotationDTO> paramList_V3 = conn.getLeadAnnotationListConceptIDList(userId, docId, 8, createdBy, bioportalOntologyID, bioportalClassIdList);
			List<AnnotationDTO> paramList_V4 = conn.getLeadAnnotationListConceptIDList(userId, docId, 9, createdBy, bioportalOntologyID, bioportalClassIdList);
			List<AnnotationDTO> paramList_V5 = conn.getLeadAnnotationListConceptIDList(userId, docId, 10, createdBy, bioportalOntologyID, bioportalClassIdList);
			List<AnnotationDTO> paramList_V6 = conn.getLeadAnnotationListConceptIDList(userId, docId, 11, createdBy, bioportalOntologyID, bioportalClassIdList);

			int i=0;
			retList.add(i++, paramList_I.get(0)); // qa_I
//			retList.add(i++, paramList_II.get(0)); // qa_II
//			retList.add(2, paramList_III.get(0)); // qa_III
//			retList.add(3, paramList_aVR.get(0)); // qa_aVR
			retList.add(i++, paramList_aVL.get(0)); // qa_aVL
			retList.add(i++, paramList_aVF.get(0)); // qa_aVF
			retList.add(i++, paramList_V1.get(0)); // qa_V1
			retList.add(i++, paramList_V2.get(0)); // qa_V2
			retList.add(i++, paramList_V3.get(0)); // qa_V3
			retList.add(i++, paramList_V4.get(0)); // qa_V4
			retList.add(i++, paramList_V5.get(0)); // qa_V5
			retList.add(i++, paramList_V6.get(0)); // qa_V6
			//*********************************************
			retList.add(i++, paramList_I.get(1)); // qd_I
			retList.add(i++, paramList_II.get(1)); // qd_II
//			retList.add(14, paramList_III.get(1)); // qd_III
//			retList.add(15, paramList_aVR.get(1)); // qd_aVR
			retList.add(i++, paramList_aVL.get(1)); // qd_aVL
			retList.add(i++, paramList_aVF.get(1)); // qd_aVF
			retList.add(i++, paramList_V1.get(1)); // qd_V1
			retList.add(i++, paramList_V2.get(1)); // qd_V2
			retList.add(i++, paramList_V3.get(1)); // qd_V3
			retList.add(i++, paramList_V4.get(1)); // qd_V4
			retList.add(i++, paramList_V5.get(1)); // qd_V5
			retList.add(i++, paramList_V6.get(1)); // qd_V6
			//*********************************************
			retList.add(i++, paramList_I.get(2)); // ra_I
			retList.add(i++, paramList_II.get(2)); // ra_II
//			retList.add(26, paramList_III.get(2)); // ra_III
//			retList.add(27, paramList_aVR.get(2)); // ra_aVR
			retList.add(i++, paramList_aVL.get(2)); // ra_aVL
			retList.add(i++, paramList_aVF.get(2)); // ra_aVF
			retList.add(i++, paramList_V1.get(2)); // ra_V1
			retList.add(i++, paramList_V2.get(2)); // ra_V2
			retList.add(i++, paramList_V3.get(2)); // ra_V3
			retList.add(i++, paramList_V4.get(2)); // ra_V4
			retList.add(i++, paramList_V5.get(2)); // ra_V5
			retList.add(i++, paramList_V6.get(2)); // ra_V6
			//*********************************************
//			retList.add(36, paramList_I.get(3)); // rd_I
//			retList.add(37, paramList_II.get(3)); // rd_II
//			retList.add(38, paramList_III.get(3)); // rd_III
//			retList.add(39, paramList_aVR.get(3)); // rd_aVR
//			retList.add(40, paramList_aVL.get(3)); // rd_aVL
//			retList.add(41, paramList_aVF.get(3)); // rd_aVF
			retList.add(i++, paramList_V1.get(3)); // rd_V1
			retList.add(i++, paramList_V2.get(3)); // rd_V2
			retList.add(i++, paramList_V3.get(3)); // rd_V3
//			retList.add(45, paramList_V4.get(3)); // rd_V4
//			retList.add(46, paramList_V5.get(3)); // rd_V5
//			retList.add(47, paramList_V6.get(3)); // rd_V6
			//*********************************************
			retList.add(i++, paramList_I.get(4)); // sa_I
			retList.add(i++, paramList_II.get(4)); // sa_II
//			retList.add(50, paramList_III.get(4)); // sa_III
//			retList.add(51, paramList_aVR.get(4)); // sa_aVR
			retList.add(i++, paramList_aVL.get(4)); // sa_aVL
			retList.add(i++, paramList_aVF.get(4)); // sa_aVF
			retList.add(i++, paramList_V1.get(4)); // sa_V1
			retList.add(i++, paramList_V2.get(4)); // sa_V2
			retList.add(i++, paramList_V3.get(4)); // sa_V3
			retList.add(i++, paramList_V4.get(4)); // sa_V4
			retList.add(i++, paramList_V5.get(4)); // sa_V5
			retList.add(i++, paramList_V6.get(4)); // sa_V6
			//*********************************************

			// un-needed code which substitutes value from R' and S' when R or S are zero.
			// Turned out that this was not the correct thing to do here. (Mike Shipway - July 16, 2014)
//			List <String> nameList = new ArrayList<String>();
//			nameList.add("Q-_AMPL"); // dummy place keeper, since Schiller doesn't seem to have negative Q entries
//			nameList.add("Q-_DUR"); //  dummy place keeper, since Schiller doesn't seem to have negative Q entries
//			nameList.add("R-_AMPL"); // Negative R_Wave_Amplitude ECGOntology:ECG_000000750
//			nameList.add("R-_DUR");  // Negative R_Wave_Duration ECGOntology:ECG_000000597
//			nameList.add("S-_AMPL"); // Negative S_Wave_Amplitude ECGOntology:ECG_000000107
//		
//			List<AnnotationDTO> negParamList_I = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 0, createdBy,  nameList);
//			List<AnnotationDTO> negParamList_II = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 1, createdBy,  nameList);
//			List<AnnotationDTO> negParamList_aVL = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 4, createdBy,  nameList);
//			List<AnnotationDTO> negParamList_aVF = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 5, createdBy,  nameList);
//			List<AnnotationDTO> negParamList_V1 = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 6, createdBy,  nameList);
//			List<AnnotationDTO> negParamList_V2 = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 7, createdBy,  nameList);
//			List<AnnotationDTO> negParamList_V3 = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 8, createdBy,  nameList);
//			List<AnnotationDTO> negParamList_V4 = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 9, createdBy,  nameList);
//			List<AnnotationDTO> negParamList_V5 = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 10, createdBy,  nameList);
//			List<AnnotationDTO> negParamList_V6 = ConnectionFactory.createConnection().getLeadAnnotationbyNameList(userId, docId, 11, createdBy,  nameList);
//
//			List<AnnotationDTO> negList= new ArrayList<AnnotationDTO>();
//			int n=0;
//			negList.add(n++, negParamList_I.get(0)); // qa_I
//			negList.add(n++, negParamList_aVL.get(0)); // qa_aVL
//			negList.add(n++, negParamList_aVF.get(0)); // qa_aVF
//			negList.add(n++, negParamList_V1.get(0)); // qa_V1
//			negList.add(n++, negParamList_V2.get(0)); // qa_V2
//			negList.add(n++, negParamList_V3.get(0)); // qa_V3
//			negList.add(n++, negParamList_V4.get(0)); // qa_V4
//			negList.add(n++, negParamList_V5.get(0)); // qa_V5
//			negList.add(n++, negParamList_V6.get(0)); // qa_V6
//			//*********************************************
//			negList.add(n++, negParamList_I.get(1)); // qd_I
//			negList.add(n++, negParamList_II.get(1)); // qd_II
//			negList.add(n++, negParamList_aVL.get(1)); // qd_aVL
//			negList.add(n++, negParamList_aVF.get(1)); // qd_aVF
//			negList.add(n++, negParamList_V1.get(1)); // qd_V1
//			negList.add(n++, negParamList_V2.get(1)); // qd_V2
//			negList.add(n++, negParamList_V3.get(1)); // qd_V3
//			negList.add(n++, negParamList_V4.get(1)); // qd_V4
//			negList.add(n++, negParamList_V5.get(1)); // qd_V5
//			negList.add(n++, negParamList_V6.get(1)); // qd_V6
//			//*********************************************
//			negList.add(n++, negParamList_I.get(2)); // ra_I
//			negList.add(n++, negParamList_II.get(2)); // ra_II
//			negList.add(n++, negParamList_aVL.get(2)); // ra_aVL
//			negList.add(n++, negParamList_aVF.get(2)); // ra_aVF
//			negList.add(n++, negParamList_V1.get(2)); // ra_V1
//			negList.add(n++, negParamList_V2.get(2)); // ra_V2
//			negList.add(n++, negParamList_V3.get(2)); // ra_V3
//			negList.add(n++, negParamList_V4.get(2)); // ra_V4
//			negList.add(n++, negParamList_V5.get(2)); // ra_V5
//			negList.add(n++, negParamList_V6.get(2)); // ra_V6
//			//*********************************************
//			negList.add(n++, negParamList_V1.get(3)); // rd_V1
//			negList.add(n++, negParamList_V2.get(3)); // rd_V2
//			negList.add(n++, negParamList_V3.get(3)); // rd_V3
//			//*********************************************
//			negList.add(n++, negParamList_I.get(4)); // sa_I
//			negList.add(n++, negParamList_II.get(4)); // sa_II
//			negList.add(n++, negParamList_aVL.get(4)); // sa_aVL
//			negList.add(n++, negParamList_aVF.get(4)); // sa_aVF
//			negList.add(n++, negParamList_V1.get(4)); // sa_V1
//			negList.add(n++, negParamList_V2.get(4)); // sa_V2
//			negList.add(n++, negParamList_V3.get(4)); // sa_V3
//			negList.add(n++, negParamList_V4.get(4)); // sa_V4
//			negList.add(n++, negParamList_V5.get(4)); // sa_V5
//			negList.add(n++, negParamList_V6.get(4)); // sa_V6
//			//*********************************************
//	
//			for(int c=0;c<retList.size();c++){
//				if( (retList.get(c).getValue() == "0") && (negList.get(c) != null) ){
//					retList.set(c, negList.get(c) );
//				}			
//			}
		} catch (DataStorageException e) {
			e.printStackTrace();
		}
		
		return retList;
	}
	
	/** Gets an ArrayList of whole record annotations, QRS Duration & QRS Axis;
	 *  which are found in the Magellan text output file.
	 * 
	 * @param userId - Id of the user who owns this data.
	 * @param docId - Document ID
	 * @param createdBy - Either original file format identifier, algorithm identifier, or user ID in the case of manual annotations.
	 * @param bioportalOntologyID - Identifier of the Ontology, e.g. "ECGT"
	 **/
	 private List<AnnotationDTO>  getMagellanRecordAnnotationList(long userId, long docId, String createdBy, String bioportalOntologyID){
//			List<AnnotationDTO> retList= new ArrayList<AnnotationDTO>();
			
			List <String> bioportalClassIdList = new ArrayList<String>();
			bioportalClassIdList.add("ECGOntology:ECG_000000072"); // QRS Duration
			bioportalClassIdList.add("ECGOntology:ECG_000000838"); // QRS Axis
			
			List<AnnotationDTO> paramList_Rec = null;
			
			try {
				paramList_Rec = ConnectionFactory.createConnection().getLeadAnnotationListConceptIDList(userId, docId, null, createdBy, bioportalOntologyID, bioportalClassIdList);
			} catch (DataStorageException e) {
				e.printStackTrace();
			}

//			retList.add(0, paramList_Rec.get(0)); // qa_I
			//*********************************************
			
			return paramList_Rec;
	 }
	 
	 
	 /** Builds the parameterList needed for Strauss-Selvester QRS-Score algorithm.
	  * 
	  * @param docId
	  * @param name
	  * @param age
	  * @param sex
	  * @param qrsd
	  * @param qrsax
	  * @param magellanLeadAnnotList
	  * @return
	  */
	 private LinkedHashMap<String, String>  buildQRS_ScoreParameterListMap(long docId, String name, String age, String sex, String qrsd, String qrsax, List<AnnotationDTO> magellanLeadAnnotList){
		 LinkedHashMap<String, String>  magellanParameters = new LinkedHashMap<String, String>();		
		 
		 magellanParameters.put("Name", name);
		 magellanParameters.put("ID", String.valueOf(docId) );
		 magellanParameters.put("age", age);
		 magellanParameters.put("sex", sex);
		 magellanParameters.put("ECG_000000072", qrsd);
		 magellanParameters.put("ECG_000000838", qrsax);
		 
		 for (AnnotationDTO a:magellanLeadAnnotList){
			 if(a != null){
				 int numIndex = a.getBioportalClassId().lastIndexOf("#");
				 String idNumber = a.getBioportalClassId().substring(numIndex+1);
				 
				 if(a.getValue() == null){
//					 magellanParameters.put(a.getBioportalClassId() + "_" + a.getLead(), "0 "); // Magellan used zeros to mark unknown or incalculable values.
					 magellanParameters.put(idNumber + "_" + a.getLead(), "0 "); // Magellan used zeros to mark unknown or incalculable values.
				 }else{
//					 magellanParameters.put(a.getBioportalClassId() + "_" + a.getLead(), a.getValue());
					 magellanParameters.put(idNumber + "_" + a.getLead(), a.getValue());
				 }
			 }
		 }
		 
		 return magellanParameters;
	 }
	 
	 public void setBackgroundQueue(List<AnalysisStatusDTO> backgroundQueue) {
		PortletSession session = (PortletSession) FacesContext.getCurrentInstance().getExternalContext().getSession(false);
		session.setAttribute("analysis.backgroundQueue", backgroundQueue);
	}
	 
	 public static List<AnalysisStatusDTO> getBackgroundQueue(){
		 PortletSession session = (PortletSession) FacesContext.getCurrentInstance().getExternalContext().getSession(false);
		 List<AnalysisStatusDTO> backgroundQueue = (List<AnalysisStatusDTO>) session.getAttribute("analysis.backgroundQueue");
		 if(backgroundQueue == null){
			 backgroundQueue = new ArrayList<AnalysisStatusDTO>();
			 session.setAttribute("analysis.backgroundQueue", backgroundQueue);
		 }
		 return backgroundQueue;
     }
	 
	 public void addToBackgroundQueue(AnalysisStatusDTO dto) {
		if(dto!=null){
			if(AnalysisManager.getBackgroundQueue() == null){
				setBackgroundQueue(new ArrayList<AnalysisStatusDTO>());
			}
			int index = AnalysisManager.getBackgroundQueue().indexOf(dto);
			if(index != -1){
				AnalysisManager.getBackgroundQueue().get(index).update(dto);
			}else{
				AnalysisManager.getBackgroundQueue().add(dto);	
			}
		}
	}

	 
//	 private String buildMagellanText(long docId, String name, String age, String sex, String qrsd, String qrsax, List<AnnotationDTO> magellanLeadAnnotList){
//		 StringBuffer sb = new StringBuffer();
//		 sb.append("13\n"
//					+"\n"
//					+"Age\n" 
//					+"Sex \n"
//					+"vrate \n"
//					+"arate \n"
//					+"pr \n"
//					+"qrsd \n"
//					+"qt \n"
//					+"qtc \n"
//					+"pax \n"
//					+"qrsax \n"
//					+"tax \n"
//					+"pdur \n"
//					+"qrstangle\n" 
//					+"\n"
//					+"8 \n"
//					+"\n"
//					+"pa  0  0  0  0  0  1  1  0  0  0  0  0\n"  
//					+"qa  1  1  0  0  1  1  1  1  1  1  1  1  \n"
//					+"qd  1  1  0  0  1  1  1  1  1  1  1  1  \n"
//					+"ra  1  1  0  0  1  1  1  1  1  1  1  1  \n"
//					+"rd  0  0  0  0  0  0  1  1  1  0  0  0  \n"
//					+"sa  1  1  0  0  1  1  1  1  1  1  1  1  \n"
//					+"rpa  1  1  0  0  1  1  1  1  1  1  1  1  \n"
//					+"spa  1  1  0  0  1  1  1  1  1  1  1  1  \n"
//					+"\n"
//					+"\n"
//					+"2 \n"
//					+"\n"
//					+"No_scode\n"  
//					+"scode\n");
//		 sb.append(name + " "); // Name
//		 sb.append(docId + " "); // ID
//		 sb.append("05/14/2010_01:59  C:/FAKEdir/FAKE.ECG "); // Date/Time, FilePath
//		 sb.append(age + " ");// age
//		 sb.append(sex + " ");// sex
//		 sb.append("0 0 0 ");// vrate, arate, pr
//		 sb.append(qrsd + " ");// qrsd
//		 sb.append("0 0 0 "); // qt, qtc, pax
//		 sb.append(qrsax + " ");// qrsax
//		 sb.append("0 0 0 0 "); // tax, pdur, qrstangle, pa_aVF, pa_V1		 		 
//		 for (AnnotationDTO a:magellanLeadAnnotList){
//			 if(a ==null){
//				 sb.append("0 "); // Magellan used zeros to mark unknown or incalculable values.
//			 }else{
//				 sb.append(a.getValue() + " ");
//			 }
//		 }
//		 sb.append("0 0 0 0 0 "); // rpa_I, rpa_II, rpa_aVL, rpa_aVF	
//		 sb.append("0 0 0 0 0 0 "); // rpa_V1, rpa_V2, rpa_V3, rpa_V4, rpa_V5, rpa_V6	
//		 sb.append("0 0 0 0 0 "); // spa_I, spa_II, spa_aVL, spa_aVF	
//		 sb.append("0 0 0 0 0 0 "); // spa_V1, spa_V2, spa_V3, spa_V4, spa_V5, spa_V6	
//		 sb.append("0 "); // No_scode, scode
//
//		 sb.append("\n");
//		 
//		 return sb.toString();
//	 }
}
