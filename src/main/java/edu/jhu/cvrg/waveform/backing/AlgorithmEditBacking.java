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
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.primefaces.event.SelectEvent;
import org.primefaces.event.UnselectEvent;

import com.liferay.portal.model.User;

import edu.jhu.cvrg.analysis.vo.AnalysisResultType;
import edu.jhu.cvrg.data.dto.AdditionalParametersDTO;
import edu.jhu.cvrg.data.dto.AlgorithmDTO;
import edu.jhu.cvrg.data.dto.ServiceDTO;
import edu.jhu.cvrg.data.factory.ConnectionFactory;
import edu.jhu.cvrg.data.util.DataStorageException;
import edu.jhu.cvrg.waveform.utility.ResourceUtility;

@ManagedBean(name = "algorithmEditBacking")
@SessionScoped
public class AlgorithmEditBacking extends BackingBean implements Serializable {
	private static final long serialVersionUID = 1183266658930656309L;
	
	private int selectedAlgorithmID=-1;
	private AlgorithmDTO selectedAlgorithm;

	private User userModel;
	
	private AlgorithmList algorithmList;
	private ServiceList serviceList;
	private int newServiceID;
	private List<FacesMessage> messages;
	
	private AdditionalParametersDTO editParameter = new AdditionalParametersDTO();
	private ServiceDTO editService = new ServiceDTO();
	
	@PostConstruct
	public void init() {
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
		}
	}

    public void onComplete() {
    	this.messages.clear();
    }


    public void onRowSelect(SelectEvent event) {  
    	this.getLog().info("selected:" + selectedAlgorithm.getDisplayShortName());
    	return;
    }  
  
    public void onRowUnselect(UnselectEvent event) {  
    	this.getLog().info("unselected:" + selectedAlgorithm.getDisplayShortName());
    	return;
    }
    
    /* ********************* Button Click handlers ************************/
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

    /** Adds a new (blank) algorithm entry to the database, then reloads the algorithm list, then opens the editing page (editM_Tabpage.xhtml) **/
    public void deleteAlgorithm(){
    	try {
			ConnectionFactory.createConnection().deleteAlgorithm(this.getSelectedAlgorithmID());
			initAlgorithmList(-1);

		} catch (DataStorageException e) {
			this.getLog().error(e.getMessage());
		}
    }

    
    /** Save the current algorithm edit and stays on current page.*/
    public void saveAlgorithm(){
    	this.getLog().info(" saveAlgorithm()");
    	if(selectedAlgorithmID>=0){
    		// edited existing algorithm
    		algorithmList.updateAlgorithmToDB(selectedAlgorithm);
    	}else{
    		// new algorithm
    	}
    	initAlgorithmList(selectedAlgorithmID);
    }
    
    public void saveParameter(){
    	if(editParameter != null){
    		int id = editParameter.getId();
    		if(id == 0){
    			this.getLog().info(" addParameter()");
    			algorithmList.addNewAlgorithmParameterToDB(editParameter, getSelectedAlgorithm().getId());
    	    }else{
    			this.getLog().info(" updateParameter(" + id + ")");
    			algorithmList.updateAlgorithmParameterToDB(editParameter, getSelectedAlgorithm().getId());
    		}
    		
    		try {
    			this.getSelectedAlgorithm().setParameters(ConnectionFactory.createConnection().getAlgorithmParameterArray(getSelectedAlgorithm().getId()));
			} catch (DataStorageException e) {
				e.printStackTrace();
			}
    	}
    }
    
    public void updateEditParameter(){
    	Map<String,String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
		int paramId = Integer.parseInt(params.get("parameterId"));
		this.getLog().info("parameterId:" + paramId);
		
		if(paramId == 0){
			editParameter = new AdditionalParametersDTO();
		}else{
			for (AdditionalParametersDTO p : this.getParameterList()) {
				if(p.getId() == paramId){
					editParameter = p;
					break;
				}
			}	
		}
	}
    
    
    public void removeParameter(){
    	Map<String,String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
		long paramId = Long.parseLong(params.get("parameterId"));
		this.getLog().info("parameterId:" + paramId);
		
		if(paramId != 0){
			try {
				ConnectionFactory.createConnection().deleteParameter(paramId);
				this.getSelectedAlgorithm().setParameters(ConnectionFactory.createConnection().getAlgorithmParameterArray(getSelectedAlgorithm().getId()));
			} catch (DataStorageException e) {
				e.printStackTrace();
			}
		}
	}
    
    
    public void saveService(){
    	if(editService != null){
    		int id = editService.getId();
    		try {
	    		if(id == 0){
	    			this.getLog().info(" addService()");
	    			serviceList.addNewServiceToDB(editService.getDisplayServiceName(), editService.getServiceName(), editService.getUrl());
	    		}else{
	    			this.getLog().info(" updateService(" + id + ")");
	    			serviceList.updateAlgorithmParameterToDB(editService);
	    		}
	    		this.getServiceList().populateServiceListFromDB();
			} catch (DataStorageException e) {
				e.printStackTrace();
			}
    	}
    }
    
    public void updateEditService(){
    	Map<String,String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
		int serviceId = Integer.parseInt(params.get("serviceId"));
		this.getLog().info("serviceId:" + serviceId);
		
		if(serviceId == 0){
			editService = new ServiceDTO();
		}else{
			for (ServiceDTO s : this.getServiceList().getAvailableServiceList()) {
				if(s.getId() == serviceId){
					editService = s;
					break;
				}
			}	
		}
	}
    
    public void deleteService(){
    	if(editService != null){
    		int id = editService.getId();
    		try {
	    		if(id == 0){
	    			this.getLog().error(" unable to deleteService(). No id selected");
	    		}else{
	    			this.getLog().info(" deleteService(" + id + ")");
	    			ConnectionFactory.createConnection().deleteservice(editService.getId());
	    		}
    			this.getServiceList().populateServiceListFromDB();
			} catch (DataStorageException e) {
				e.printStackTrace();
			}
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
    
    /* ******************* getters and setters **************************/    
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
	
	public List<SelectItem> getResultTypes(){
		List<SelectItem> result = new ArrayList<SelectItem>();
		for (AnalysisResultType type : AnalysisResultType.values()) {
			result.add(new SelectItem(type, type.name().replace('_', ' ')));
		}
		return result;
	}

	public AdditionalParametersDTO getEditParameter() {
		return editParameter;
	}

	public void setEditParameter(AdditionalParametersDTO newParameter) {
		this.editParameter = newParameter;
	}

	public ServiceDTO getEditService() {
		return editService;
	}

	public void setEditService(ServiceDTO editService) {
		this.editService = editService;
	}
}
