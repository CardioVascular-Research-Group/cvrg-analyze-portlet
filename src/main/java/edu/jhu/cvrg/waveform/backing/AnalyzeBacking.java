package edu.jhu.cvrg.waveform.backing;
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
 * @author Chris Jurado, Scott Alger
 * 
 */
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.component.UIComponent;
import javax.faces.component.UISelectItem;
import javax.faces.component.html.HtmlForm;
import javax.faces.component.html.HtmlOutputLabel;
import javax.faces.component.html.HtmlOutputText;
import javax.faces.component.html.HtmlPanelGrid;
import javax.faces.component.html.HtmlPanelGroup;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;

import org.primefaces.component.dialog.Dialog;
import org.primefaces.component.inputtext.InputText;
import org.primefaces.component.selectbooleancheckbox.SelectBooleanCheckbox;
import org.primefaces.component.selectoneradio.SelectOneRadio;
import org.primefaces.context.RequestContext;
import org.primefaces.event.NodeSelectEvent;
import org.primefaces.event.NodeUnselectEvent;
import org.primefaces.event.SelectEvent;
import org.primefaces.event.UnselectEvent;
import org.primefaces.model.TreeNode;

import com.liferay.portal.model.User;

import edu.jhu.cvrg.data.dto.AdditionalParametersDTO;
import edu.jhu.cvrg.data.dto.AlgorithmDTO;
import edu.jhu.cvrg.data.dto.AnalysisStatusDTO;
import edu.jhu.cvrg.data.factory.Connection;
import edu.jhu.cvrg.data.factory.ConnectionFactory;
import edu.jhu.cvrg.data.util.DataStorageException;
import edu.jhu.cvrg.waveform.main.AnalysisManager;
import edu.jhu.cvrg.waveform.model.DocumentDragVO;
import edu.jhu.cvrg.waveform.model.FileTreeNode;
import edu.jhu.cvrg.waveform.model.LocalFileTree;
import edu.jhu.cvrg.waveform.utility.ResourceUtility;
import edu.jhu.cvrg.waveform.utility.ServerUtility;

@ViewScoped
@ManagedBean(name = "analyzeBacking")
public class AnalyzeBacking extends BackingBean implements Serializable {

	private static final long serialVersionUID = -4006126553152259063L;

	private HtmlPanelGroup panelParameterSet;

	private AlgorithmDTO[] selectedAlgorithms;
	private ArrayList<DocumentDragVO> tableList;

	private LocalFileTree fileTree;
	private User userModel;
	
	private AlgorithmList algorithmList;
	private int algorithmToEditID = -1;
	private boolean setparameterdisplayed=false;
	private AnalysisManager analysisManager;	
	private List<FacesMessage> messages;
	
	@PostConstruct
	public void init() {
		userModel = ResourceUtility.getCurrentUser();
		if(userModel != null){
			if(fileTree == null){
				fileTree = new LocalFileTree(userModel.getUserId());
			}
			if(algorithmList == null){
				algorithmList = new AlgorithmList();
			}
			this.getLog().info("Number of algorithms in list:" + algorithmList.getAvailableAlgorithms().size());
			messages = new ArrayList<FacesMessage>();
			loadBackgroundQueue();
		}
		// TODO: **** testing creating front end controls purely from Java, for parameter editing.
	}

	private void loadParameterSetPanel(int selectedAlgID){
		if (selectedAlgID != (-1)){
			
			HtmlForm f = (HtmlForm) FacesContext.getCurrentInstance().getViewRoot().findComponent("dialogForm");
			
			for (UIComponent c : f.getChildren()) {
				if(c instanceof Dialog){
					Dialog d = (Dialog) c;
					panelParameterSet = (HtmlPanelGroup) d.findComponent("dialogContent");
					break;
				}
			}  
			
			int algIndex = -1;
			if(algorithmList.getAvailableAlgorithms().size()>1){
				for(int i=0;i<algorithmList.getAvailableAlgorithms().size();i++){
					if(algorithmList.getAvailableAlgorithms().get(i).getId() == selectedAlgID){
						algIndex = i;
						break;
					}
				}			
			}
			ArrayList<AdditionalParametersDTO> paramList = algorithmList.getAvailableAlgorithms().get(algIndex).getParameters();
			
			HtmlPanelGrid grid = new HtmlPanelGrid();
			grid.setId("gridOne"+algIndex);
			grid.setBorder(4);
			grid.setColumns(3);
	
			for(AdditionalParametersDTO p:paramList){
				grid.getChildren().add(makeLabel("(" + p.getParameterFlag() + ") " + p.getDisplayShortName(), p.getToolTipDescription()));
				
				String type = p.getType(); /** MUST BE text, integer, float, boolean, select, data_column, or drill_down  BUT NOT genomebuild, hidden, baseurl, file, data. **/
				if(type.equals("text")){
					grid.getChildren().add(showInputText(String.valueOf(p.getId()),p.getParameterUserSpecifiedValue(),p.getLongDescription()));
				}
				if(type.equals("integer")){
					grid.getChildren().add(showInputText(String.valueOf(p.getId()),p.getParameterUserSpecifiedValue(),p.getLongDescription()));
				}
				if(type.equals("float")){
					grid.getChildren().add(showInputText(String.valueOf(p.getId()),p.getParameterUserSpecifiedValue(),p.getLongDescription()));
				}
				if(type.equals("boolean")){
					grid.getChildren().add(showCheckbox(String.valueOf(p.getId()), p.getParameterUserSpecifiedValue(),p.getLongDescription()));
				}
				if(type.equals("select")){
	//				grid.getChildren().add(showInputText(String.valueOf(p.getId()),p.getParameterUserSpecifiedValue(),p.getLongDescription()));
				}
				if(type.equals("data_column")){
	//				grid.getChildren().add(showInputText(String.valueOf(p.getId()),p.getParameterUserSpecifiedValue(),p.getLongDescription()));
				}
				if(type.equals("drill_down")){
	//				grid.getChildren().add(showInputText(String.valueOf(p.getId()),p.getParameterUserSpecifiedValue(),p.getLongDescription()));
				}
				grid.getChildren().add(makeLabel(p.getLongDescription(), p.getLongDescription()));
	
			}
			if(panelParameterSet.getChildren() != null && !panelParameterSet.getChildren().isEmpty()){
				panelParameterSet.getChildren().remove(0);
			}
		    panelParameterSet.getChildren().add(grid);
		    RequestContext.getCurrentInstance().update("dialogContent");
		}
	}

	private HtmlOutputLabel makeLabel(String value, String title){
        HtmlOutputLabel label = new HtmlOutputLabel();
        label.setValue(value);
        label.setTitle(title);
        return label;
	}
	private InputText showInputText(String id, String value, String label){
		InputText it = new InputText();
		try {
			it.setId("text" + id);
			it.setValue(value);
			it.setLabel(label);
		} catch (IllegalArgumentException  e) {
			e.printStackTrace();
			this.getLog().fatal("ArgumentException. " + e.getMessage());
		}catch (Exception e){
			this.getLog().fatal("Fatal error. " + e.getMessage());
			ServerUtility.logStackTrace(e, this.getLog());
		}
		
		return it;
	}
	
	private SelectBooleanCheckbox showCheckbox(String id, String value, String label){
		SelectBooleanCheckbox cb = new SelectBooleanCheckbox();
		try{
			cb.setId("boolean" + id);
			cb.setSelected("on".equals(value));
			cb.setLabel(label);
			
		} catch (IllegalArgumentException  e) {
			e.printStackTrace();
			this.getLog().fatal("ArgumentException. " + e.getMessage());
		}catch (Exception e){
			this.getLog().fatal("Fatal error. " + e.getMessage());
			ServerUtility.logStackTrace(e, this.getLog());
		}
		
		return cb;
	}
	
//	private SelectBooleanCheckbox showCheckbox(){
//		SelectBooleanCheckbox cb = new SelectBooleanCheckbox();
//		cb.setId("cb1");
//		cb.setValue("Checkbox1");
//		cb.setLabel("Checkbox1 Label");
////		cb.setRendered(true);
//		
//		return cb;
//	}

	private SelectOneRadio showStronglyQuestion(){
		SelectOneRadio rb = new SelectOneRadio();
		rb.setRendered(true);
		
		UISelectItem itemOne = new UISelectItem();
		itemOne.setValue("1");
		itemOne.setItemLabel("Strongly Agree");
		rb.getChildren().add(itemOne);
		
		UISelectItem itemTwo = new UISelectItem();
		itemTwo.setItemValue("2");
		itemTwo.setItemLabel("Agree");
		rb.getChildren().add(itemTwo);
		
		UISelectItem itemThree = new UISelectItem();
		itemThree.setItemValue("3");
		itemThree.setItemLabel("Neither Agree nor Disagree");
		rb.getChildren().add(itemThree);
		
		UISelectItem itemFour = new UISelectItem();
		itemFour.setItemValue("4");
		itemFour.setItemLabel("Disagree");
		rb.getChildren().add(itemFour);
		
		UISelectItem itemFive = new UISelectItem();
		itemFive.setItemValue("5");
		itemFive.setItemLabel("Strongly Disagree");
		rb.getChildren().add(itemFive);
		
		return rb;
	}


	public void startAnalysis() {
		messages.clear();

		if(tableList == null || tableList.isEmpty()){
			this.getLog().info("No files selected.  List is empty.");
			messages.add(new FacesMessage(FacesMessage.SEVERITY_WARN, "Analysis Error" , "No file selected."));
		}
		
		if(selectedAlgorithms == null || selectedAlgorithms.length == 0){
			this.getLog().info("Algorithms selected is null.");
			messages.add(new FacesMessage(FacesMessage.SEVERITY_WARN, "Analysis Error" , "No algorithm(s) selected."));
		}
		
		RequestContext context = RequestContext.getCurrentInstance();
		
		if(messages == null || messages.size() == 0){
			analysisManager = new AnalysisManager();
			analysisManager.performAnalysis(tableList,  userModel.getUserId(), selectedAlgorithms);
			context.execute("startListening();");
		}else{
			ResourceUtility.showMessages("Warning", messages);
			context.execute("PF('dlg2').hide();");
		}
		
	}

	public void generateParameterSetting(){
		Map<String,String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
		String algorithmParam = params.get("algorithmId");
		this.getLog().info("algorithmID:" + algorithmParam);
		algorithmToEditID = Integer.valueOf(algorithmParam);
		loadParameterSetPanel(algorithmToEditID);
	}
	
	public void generateErrorPanel(){
		Map<String,String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
		String docId = params.get("docId");
		String x = params.get("x");
		String y = params.get("y");
		this.getLog().info("documentRecordId:" + docId);
		loadErrorPanel(docId, x, y);
	}
	
	//action listener event
	public void attrListener(ActionEvent event){
 
		int algorithmid = (Integer) event.getComponent().getAttributes().get("algorithmid");
		this.getLog().info("attrListener() algorithmid:" + algorithmid);
	}

	
    public void updateParameter(int id){
    	this.getLog().info(" updateParameter(" + id + ")");
    }

	
	public void folderSelect(NodeSelectEvent event){
		TreeNode node = event.getTreeNode();
		if(!node.getType().equals("document")){
			fileTree.selectAllChildNodes(node);
		}
	}
	
	public void folderUnSelect(NodeUnselectEvent event){
		TreeNode node = event.getTreeNode();
		node.setSelected(false);
		if(!node.getType().equals("document")){
			fileTree.unSelectAllChildNodes(node);
		}
	}
	
    public void onRowSelect(SelectEvent event) {  
    	//Do not delete this method.  The listener is present to force a form submit on select.
    	return;
    }  
  
    public void onRowUnselect(UnselectEvent event) {  
    	//Do not delete this method.  The listener is present to force a form submit on un-select.
    	return;
    }  

	public ArrayList<DocumentDragVO> getTableList() {
		return tableList;
	}

	public void refreshStudieList(ActionEvent actionEvent) {
		tableList.removeAll(tableList);
	}

	public LocalFileTree getFileTree() {
		return fileTree;
	}

	public void setFileTree(LocalFileTree fileTree) {
		this.fileTree = fileTree;
	}

	public void setTableList(ArrayList<DocumentDragVO> tableList) {
		this.tableList = tableList;
	}

	public AlgorithmDTO[] getSelectedAlgorithms() {
		return selectedAlgorithms;
	}

	public void setSelectedAlgorithms(AlgorithmDTO[] selectedAlgorithms) {
		this.selectedAlgorithms = selectedAlgorithms;
	}

	public AlgorithmList getAlgorithmList() {
		return algorithmList;
	}

	public void setAlgorithmList(AlgorithmList algorithmList) {
		this.algorithmList = algorithmList;
	}
	
	public User getUser(){
		return userModel;
	}

	public HtmlPanelGroup getPanelParameterSet() {
//		this.getLog().info("getPanelParameterSet() algorithmID:" + algorithmToEditID);
		if(algorithmToEditID !=-1)	{
			this.getLog().info("getPanelParameterSet() algorithmID:" + algorithmToEditID);
			loadParameterSetPanel(algorithmToEditID);
		}else{
			loadParameterSetPanel(47);
		}

    	return panelParameterSet;
    }

    public void setPanelParameterSet(HtmlPanelGroup panelParameterSet) {
		this.panelParameterSet = panelParameterSet;
	}
	
	
	public int getAlgorithmToEditID() {
		return algorithmToEditID;
	}

	public void setAlgorithmToEditID(int algorithmToEditID) {
		this.algorithmToEditID = algorithmToEditID;
	}

	public boolean isSetparameterdisplayed() {
		return setparameterdisplayed;
	}

	public void setSetparameterdisplayed(boolean setparameterdisplayed) {
		this.setparameterdisplayed = setparameterdisplayed;
	}

	public void updateProgressBar() {  
    	int progress = 0;
    	if(analysisManager != null){
	        if(analysisManager.getTotal() > 0){
	        	int done = analysisManager.getDone();
	        	if(done>0){
	        		progress = (100 * done)/analysisManager.getTotal();
	        	}
	        }
	        
	        if(progress > 100){
	        	progress = 100;
	        }
	        if(progress>0){
		        RequestContext context = RequestContext.getCurrentInstance();  
		        context.execute("PF(\'pbClient\').setValue("+progress+");");
	        }
    	}
    }  
  
    public void onComplete() {
    	
    	int failed = 0;
    	if(analysisManager != null){
	    	List<String> messages = analysisManager.getMessages();
	    	if(messages != null){
	    		for (String m : messages) {
					this.messages.add(new FacesMessage(FacesMessage.SEVERITY_ERROR, "Analysis Error", m));
				}
	    		failed = messages.size();
	    	}
	    	
	    	ResourceUtility.showMessages("Analysis Completed ["+analysisManager.getTotal()+" Analysis - "+failed+" fail(s)]", this.messages);
		}
    	
    	if(tableList != null){
    		tableList.clear();	
    	}
    	
		selectedAlgorithms = null;
    	this.messages.clear();
    }
    
    public void treeToTable() {
        Map<String,String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String property = params.get("property");
        String type = params.get("type");
        
        if(property!=null && !property.isEmpty()){
        	try {
				Connection con = ConnectionFactory.createConnection();
				
				if(tableList == null){
					tableList = new ArrayList<DocumentDragVO>();
				}
				
				DocumentDragVO vo = null;
				
				if("leaf".equals(type)){
					FileTreeNode node = fileTree.getNodeByReference(property);
					if(node != null){
						vo = new DocumentDragVO(node, con.getDocumentRecordById(node.getDocumentRecordId()));
						if(!tableList.contains(vo)){
							tableList.add(vo);	
						}
					}	
				}else if("parent".equals(type)){
					List<FileTreeNode> nodes = fileTree.getNodesByReference(property);
					if(nodes!=null){
						for (FileTreeNode node : nodes) {
							
							vo = new DocumentDragVO(node, con.getDocumentRecordById(node.getDocumentRecordId()));
							if(!tableList.contains(vo)){
				        		tableList.add(vo);	
				        	}			
						}
					}
				}
			} catch (DataStorageException e) {
				this.getLog().error("Error on node2dto conversion. " + e.getMessage());
			}
        }else{
        	System.err.println("DRAGDROP = ERROR");
        }
    }
    
    public void removeTableItem(){
    	Map<String,String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String index = params.get("index");
        
    	if(index != null ){
    		int indexTableToRemove = Integer.parseInt(index);
    		
    		if(indexTableToRemove >= 0 && (tableList != null && tableList.size() > indexTableToRemove)){
    			tableList.remove(indexTableToRemove);
    		}
    	}
    }
    
    public void saveSelectedSettings(){

    	Map<String,String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
    	AlgorithmDTO a = this.getAlgorithmList().getAlgorithmByID(algorithmToEditID);
    	
    	resetSelectedSettings();
    	
    	for (String key : params.keySet()) {
    		int formRefIndex = key.indexOf(":dialogForm:");
    		if(formRefIndex != -1){
    			String idStr = key.substring(formRefIndex+12).replaceAll("\\D", "");
    			
    			if(!idStr.isEmpty()){
	    			int componentId = Integer.valueOf(idStr);
	    			
	    			for (AdditionalParametersDTO p : a.getParameters()) {
	    				if(componentId == p.getId()){
	    					p.setParameterUserSpecifiedValue(params.get(key));
	    					break;
	    				}
	    			}
    			}
    		}
		}
    }
    
    public void resetSelectedSettings(){
    	
		AlgorithmDTO a = this.getAlgorithmList().getAlgorithmByID(algorithmToEditID);
		
		if(a != null){
			for (AdditionalParametersDTO p : a.getParameters()) {
				p.setParameterUserSpecifiedValue(null);
			}	
		}
	}
    
    public String getSummary(){
    	int done = 0;
    	int error = 0;
    	int total = 0;
    	List<AnalysisStatusDTO> backgroundQueue = this.getBackgroundQueue();
    	if(backgroundQueue!=null && !backgroundQueue.isEmpty()){
    		StringBuilder sb = new StringBuilder();
    		for (AnalysisStatusDTO u : backgroundQueue) {
    			if(u != null){
					done += u.getDoneAnalysis();
					error+= u.getErrorAnalysis();
					total += u.getTotalAnalysis();
				}
			}
    		sb.append("Summary ").append(total);
    		
    		if(total > 1){
    			sb.append(" items [");
    		}else{
    			sb.append(" item [");
    		}
    		
    		sb.append(done).append(" done - ").append(error);
    		
    		if(error > 1){
    			sb.append(" fail(s)]");
    		}else{
    			sb.append(" fail]");
    		}
    		
    		return sb.toString();
    	}
    	
    	
    	return null;
    }
    
    public List<String> getErrorList(){
		List<String> errorList = null;

    	List<AnalysisStatusDTO> backgroundQueue = this.getBackgroundQueue();
    	if(backgroundQueue!=null && !backgroundQueue.isEmpty()){
    		errorList = new ArrayList<String>();
			for (AnalysisStatusDTO u : backgroundQueue) {
				if(u != null){
	    			if(u.getMessages() != null){
	    				for(String m : u.getMessages()){
	    					if(m != null){
	    	    				errorList.add(m);
	    					}
	    				}
					}
				}
			}
    	}
		
		return errorList;
    }
    
    public void loadBackgroundQueue(){
		Set<Long> listenIds = null;
		
		List<AnalysisStatusDTO> backgroundQueue = this.getBackgroundQueue();
		
        if(backgroundQueue != null && !backgroundQueue.isEmpty()){
        	listenIds = new HashSet<Long>();
        	for (AnalysisStatusDTO s : backgroundQueue) {
				if(s != null && s.getAnalysisIds() != null){
					listenIds.addAll(s.getAnalysisIds());
				}
			}
        	
        	int total = 0;
            if(listenIds != null && !listenIds.isEmpty()){
            	try {
    				List<AnalysisStatusDTO> tmpBackgroundQueue  = ConnectionFactory.createConnection().getAnalysisStatusByUserAndAnalysisId(userModel.getUserId(), listenIds);
    				if(tmpBackgroundQueue != null){
    			        for (AnalysisStatusDTO a : tmpBackgroundQueue) {
    						if(backgroundQueue.contains(a)){
    							backgroundQueue.get(backgroundQueue.indexOf(a)).update(a);
    						}
    						total += a.getTotalAnalysis();
    					}
    		        }else{
    		        	backgroundQueue.clear();
    		        }
            	} catch (DataStorageException e) {
    				this.getLog().error("Error on load the background analysis queue. " + e.getMessage());
    			}
            }
            
        	boolean stopListening = false;
        	for (AnalysisStatusDTO a : backgroundQueue) {
        		stopListening = (a.getErrorAnalysis() + a.getDoneAnalysis() == a.getTotalAnalysis());
        		if(!stopListening){
        			break;
        		}
			}
        	
        	if(stopListening){
        		RequestContext context = RequestContext.getCurrentInstance();
    			context.execute("stopListening();");
    			context.execute("totalFiles = " + total);
        	}
        
        }
        
	}

	public List<AnalysisStatusDTO> getBackgroundQueue(){
		return AnalysisManager.getBackgroundQueue();
    }
    
	public void removeBackgroundQueueItem(){
    	Map<String,String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String index = params.get("index");
        
    	if(index != null ){
    		int indexTableToRemove = Integer.parseInt(index);
    		
    		List<AnalysisStatusDTO> backgroundQueue = this.getBackgroundQueue();
    		
    		if(indexTableToRemove >= 0 && (backgroundQueue != null && backgroundQueue.size() > indexTableToRemove)){
    			backgroundQueue.remove(indexTableToRemove);
    		}
    		
    		RequestContext context = RequestContext.getCurrentInstance();
			context.execute("removeFile();");
    	}
    }
    
    public void removeAllDoneItem(){
    	List<AnalysisStatusDTO> backgroundQueue = this.getBackgroundQueue();
    	
		if(backgroundQueue != null && !backgroundQueue.isEmpty()){
			int total = 0;
			List<AnalysisStatusDTO> toRemove = new ArrayList<AnalysisStatusDTO>();
			for (AnalysisStatusDTO dto : backgroundQueue) {
				if(dto.getErrorAnalysis() + dto.getDoneAnalysis() == dto.getTotalAnalysis()){
					toRemove.add(dto);
				}else{
					total += dto.getTotalAnalysis();
				}
			}
			backgroundQueue.removeAll(toRemove);
			
			RequestContext context = RequestContext.getCurrentInstance();
			context.execute("totalFiles = " + total);
		}
	}
    
    public boolean isShowBackgroundPanel(){
    	return this.getBackgroundQueue() != null && !this.getBackgroundQueue().isEmpty(); 
    }
    
    public void loadErrorPanel(String docId, String x, String y){
    	
    	HtmlForm f = (HtmlForm) FacesContext.getCurrentInstance().getViewRoot().findComponent("errorPanelForm");
    	HtmlPanelGroup panelContent = null;
    	
		for (UIComponent c : f.getChildren()) {
			if(c instanceof Dialog){
				Dialog d = (Dialog) c;
				d.setPosition(x+','+y);
				panelContent = (HtmlPanelGroup) d.findComponent("panelContent");
				break;
			}
		}
    	
    	if(panelContent != null){
    		int index = this.getBackgroundQueue().indexOf(new AnalysisStatusDTO(Long.valueOf(docId), null, 0, 0, 0));
    		if(index != -1){
    			HtmlOutputText text = new HtmlOutputText();
    			text.setEscape(false);
    			StringBuilder sb = new StringBuilder();
    			AnalysisStatusDTO status = this.getBackgroundQueue().get(index);
    			sb.append("<ul>");
    			for (String m : status.getMessages()) {
    				if(m!=null){
						sb.append("<li>");
						sb.append(m);
						sb.append("</li>");
    				}
				}
    			sb.append("</ul>");
    			text.setValue(sb.toString());
    			
    			if(panelContent.getChildren() != null && !panelContent.getChildren().isEmpty()){
        			panelContent.getChildren().remove(0);
    			}
        		panelContent.getChildren().add(text);
    		    RequestContext.getCurrentInstance().update("commonErrorPanel");
    		}
    	}
    }

    
//	public void initAlgorithmList(int selectedAlgID){
//		System.out.println("passed param:" + selectedAlgID);
//	}
}

