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
 * @author Michael Shipway
 * 
 */
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;

import org.primefaces.event.SelectEvent;
import org.primefaces.event.UnselectEvent;

import com.liferay.portal.model.User;

import edu.jhu.cvrg.data.dto.AdditionalParametersDTO;
import edu.jhu.cvrg.data.dto.AlgorithmDTO;
import edu.jhu.cvrg.data.dto.ServiceDTO;
import edu.jhu.cvrg.data.util.DataStorageException;
import edu.jhu.cvrg.waveform.utility.ResourceUtility;

@ManagedBean(name = "algorithmEditBacking")
@SessionScoped
public class AlgorithmEditBacking extends BackingBean implements Serializable {
	private static final long serialVersionUID = 1183266658930656309L;
	
	private int selectedAlgorithmID=-1;
	private AlgorithmDTO selectedAlgorithm;
//	private int selectedServiceID;
//	private Service selectedService;

	private User userModel;
	
	private AlgorithmList algorithmList;
	private ServiceList serviceList;
	private int newServiceID;
	private List<FacesMessage> messages;

	@PostConstruct
	public void init() {
		// System.out.println("AlgorithmEditBacking.init()");
		userModel = ResourceUtility.getCurrentUser();
		if(userModel != null){
			if(serviceList == null){
				serviceList = new ServiceList();
			}
			if(algorithmList == null){
				initAlgorithmList(-1); // always start with default
			}
			messages = new ArrayList<FacesMessage>();
		}
	}

	private void initAlgorithmList(int selectedAlgID){
		algorithmList = new AlgorithmList();
		if(algorithmList.getAvailableAlgorithms().size()>1){
			setSelectedAlgorithm(algorithmList.getAvailableAlgorithms().get(0)); // default to first in list
			for(AlgorithmDTO a:algorithmList.getAvailableAlgorithms()){
				if(a.getId() == selectedAlgID){
					setSelectedAlgorithm(a);
				}
			}			
			selectedAlgorithmID = getSelectedAlgorithm().getId();
//			selectedServiceID = getSelectedAlgorithm().getServiceID();
//			for(Service s:serviceList.getAvailableServiceList()){
//				if(s.getId() == selectedServiceID){
//					setSelectedService(s);
//				}
//			}			
			
		}
	}

    public void onComplete() {
    	
    	int failed = 0;
//    	List<String> messages = analysisManager.getMessages();
//    	if(messages != null){
//    		for (String m : messages) {
//				this.messages.add(new FacesMessage(FacesMessage.SEVERITY_ERROR, "Analysis Error", m));
//			}
//    		failed = messages.size();
//    	}
//    	
//    	ResourceUtility.showMessages("Analysis Completed ["+analysisManager.getTotal()+" Analysis - "+failed+" fail(s)]", this.messages);
//		tableList.clear();
//		selectedAlgorithms = null;
    	// System.out.println("AlgorithmEditBacking.onComplete algorithm:" + selectedAlgorithm.getDisplayShortName());
    	this.messages.clear();
    }


    public void onRowSelect(SelectEvent event) {  
    	//Do not delete this method.  The listener is present to force a form submit on select.
    	// System.out.println("AlgorithmEditBacking.onRowSelect() algorithm:" + ((Algorithm) event.getObject()).getDisplayShortName());
//    	setSelectedAlgorithm((Algorithm) event.getObject());
    	this.getLog().info("selected:" + selectedAlgorithm.getDisplayShortName());
    	return;
    }  
  
    public void onRowUnselect(UnselectEvent event) {  
    	//Do not delete this method.  The listener is present to force a form submit on un-select.
    	this.getLog().info("unselected:" + selectedAlgorithm.getDisplayShortName());

    	return;
    }  
    /********************** Button Click handlers ************************/
    // template for 
//    public void doAction() {
//    	this.getLog().info("Done");
//    }
    
    public String editRequiredElements(){
    	this.getLog().info("+ nextView: editM_Tabpage"); 
    	return "editM_Tabpage";
    }

    /** Adds a new (blank) algorithm entry to the database, then reloads the algorithm list, then opens the editing page (editM_Tabpage.xhtml) **/
    public String addNewAlgorithm(){
    	int algID = algorithmList.addNewAlgorithmToDB(selectedAlgorithm.getServiceID()); // use the serviceID of the last selected algorithm, as the odds are good it will be the right one. 
    	initAlgorithmList(algID);
    	this.getLog().info("+ nextView: editM_Tabpage"); 
    	return "editM_Tabpage";
    }

    /** Save the current algorithm edit and stays on current page.
     * 
     */
    public void saveAlgorithm(){
    	this.getLog().info(" saveAlgorithm()");
    	if(selectedAlgorithmID>=0){
    		// edited existing algorithm
//    		selectedAlgorithm.setServiceID(selectedServiceID);
    		algorithmList.updateAlgorithmToDB(selectedAlgorithm);
    	}else{
    		// new algorithm
    	}
    	initAlgorithmList(selectedAlgorithmID);
    }
    

    public void addParameter(){
    	this.getLog().info(" addParameter()");
    	AdditionalParametersDTO newParam = new AdditionalParametersDTO();
    	newParam.setDisplayShortName("REPLACE");
    	newParam.setLongDescription("REPLACE");
    	newParam.setParameterFlag("REPLACE");
    	newParam.setParameterDefaultValue("REPLACE");
    	newParam.setType("text");
    	
    	algorithmList.addNewAlgorithmParameterToDB(newParam, getSelectedAlgorithm().getId());
    	algorithmList.populateAlgorithmsFromDB();
    }
    

    public void updateParameter(int id){
    	this.getLog().info(" updateParameter(" + id + ")");
    	for(AdditionalParametersDTO ap : selectedAlgorithm.getParameters()){
    		if(ap.getId() == id){
    	    	this.getLog().info(" name:" + ap.getDisplayShortName());
    	    	this.getLog().info(" tooltip:" + ap.getToolTipDescription());
    	    	this.getLog().info(" longDesc:" + ap.getLongDescription());
    		
    	    	algorithmList.updateAlgorithmParameterToDB(ap, getSelectedAlgorithm().getId());
    	    	algorithmList.populateAlgorithmsFromDB();
    	    	break;
    		}
    	}
    }
    
    
    public void addService(){
    	try {
			this.getLog().info(" addService()");
			String uiName = "REPLACE";
			String wsName = "REPLACE";
			String url = "REPLACE";
			newServiceID = serviceList.addNewServiceToDB(uiName, wsName, url);
			serviceList = new ServiceList(); // reload
		} catch (DataStorageException e) {
			e.printStackTrace();
		}
    }
    
    public void updateService(int id){
    	this.getLog().info(" updateService(" + id + ")");
    	try {
			for(ServiceDTO sl:serviceList.getAvailableServiceList()){
				if(sl.getId() == id){
			    	this.getLog().info(" Display name:" + sl.getDisplayServiceName());
			    	this.getLog().info(" Service's Name:" + sl.getServiceName());
			    	this.getLog().info(" URL:" + sl.getUrl());
				
			    	serviceList.updateAlgorithmParameterToDB(sl);
			    	algorithmList.populateAlgorithmsFromDB();
			    	break;
				}
			}
		} catch (DataStorageException e) {
			e.printStackTrace();
		}
    }
    
    
	/** Loads the main Algorithm listing view, the start of the edit mode, without saving.
     * Handles onclick event for the button "btnEditMain". 
     */
    public String loadMainEdit(){
		return "edit";
    }
    
    
	/** Loads the edit screen for the Long algorithm description.
     * Handles onclick event for the button "btnEditLongDesc" in the edit.xhtml view.
     */
    public String editLongDesc(){
    	String nextView= null;
    	this.getLog().info("editLongDesc()");
    	
    	if(selectedAlgorithmID >= 0){
    		this.getLog().info("+ selected algorithm:" + selectedAlgorithm.getDisplayShortName());
	   		nextView = "editB_LongDescription";
    	}else{
    		FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Error" , "<br />Please select an algorithm to edit."));	
    	}
    	
    	this.getLog().info("+ nextView:" + nextView); 
		return nextView;
    }
	
    
    public String editParameterList(){
    	this.getLog().info("+ nextView: editC_ParameterList"); 
    	return "editC_ParameterList";
    }
    
    public String editServiceList(){
    	this.getLog().info("+ nextView: editD_ServiceList"); 
    	return "editD_ServiceList";
    }
    
/******************** getters and setters **************************/    
	public int getSelectedAlgorithmID() {
		return selectedAlgorithmID;
	}

	public void setSelectedAlgorithmID(int algorithmID) {
		this.selectedAlgorithmID = algorithmID;
	}

	public AlgorithmDTO getSelectedAlgorithm() {
		return selectedAlgorithm;
	}

	public void setSelectedAlgorithm(AlgorithmDTO selectedAlgorithm) {
		this.selectedAlgorithm = selectedAlgorithm;
		this.selectedAlgorithmID = getSelectedAlgorithm().getId();
		int serviceID = getSelectedAlgorithm().getServiceID();
//		for (Service s:serviceList.getAvailableServiceList()){
//			if(serviceID ==s.id) {
//				this.selectedService = s;
//			}
//		}
		// System.out.println("AlgorithmEditBacking.setSelectedAlgorithm() algorithm:" + selectedAlgorithm.getDisplayShortName());
	}
	
	public int getParameterCount(){
		if(selectedAlgorithmID == -1) return 0;
		return this.selectedAlgorithm.getParameters().size();
	}
	
	public List<AdditionalParametersDTO> getParameterList(){
		return this.selectedAlgorithm.getParameters();
	}

	public ServiceDTO getSelectedService() {
		return serviceList.getServiceByID(selectedAlgorithm.getServiceID());
	}

//	public void setSelectedService(Service selectedService) {
//		this.selectedService = selectedService;
//	}

	public AlgorithmList getAlgorithmList() {
		return algorithmList;
	}

	public void setAlgorithmList(AlgorithmList algorithmList) {
		this.algorithmList = algorithmList;
	}
	
	public ServiceList getServiceList() {
		return serviceList;
	}

	public void setServiceList(ServiceList serviceList) {
		this.serviceList = serviceList;
	}

	public User getUser(){
		return userModel;
	}

	public ServiceDTO getNewService() {
		return serviceList.getServiceByID(newServiceID);
	}

//	public int getSelectedServiceID() {
//		return selectedServiceID;
//	}
//	public void setSelectedServiceID(int selectedServiceID) {
//		this.selectedServiceID = selectedServiceID;
//	}
}
