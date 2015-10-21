package edu.jhu.cvrg.waveform.backing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import edu.jhu.cvrg.data.dto.ServiceDTO;
import edu.jhu.cvrg.data.factory.Connection;
import edu.jhu.cvrg.data.factory.ConnectionFactory;
import edu.jhu.cvrg.data.util.DataStorageException;

/** 
 * 
 * @author Michael Shipway
 */
public class ServiceList implements Serializable{
	
	private static final long serialVersionUID = -4006126323152259063L;
	
	private List<ServiceDTO> availableServiceList = new ArrayList<ServiceDTO>();

	public ServiceList() {

		try {
			populateServiceListFromDB();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/** Returns a single algorithm based on it's Primary Key.
	 * 
	 * @param id - the primary key, as found in the algorithm database table.
	 * @return - the selected algorithm.
	 * @author Michael Shipway
	 */
	public ServiceDTO getServiceByID(int id){
		for(ServiceDTO service : availableServiceList){
			if(service.getId()==id){
				return service;
			}
		}
		return null;
	}
	

	/** populates the availableService List from the waveform3 database.
	 * Instead of from the web service's AlgorithmDetails method.
	 * @author Michael Shipway
	 */
	public void populateServiceListFromDB(){
		try {
			Connection dbUtility = ConnectionFactory.createConnection();
			List<ServiceDTO> algList = dbUtility.getAvailableServiceList(-1);
			availableServiceList = algList;
		} catch(Exception ex) {
			ex.printStackTrace();
		}	
	}

	public List<ServiceDTO> getAvailableServiceList() {
		return availableServiceList;
	}

	public void setAvailableServiceList(List<ServiceDTO> availableServiceList) {
		this.availableServiceList = availableServiceList;
	}
	
	/** Adds a new algorithm entry to the database with blatantly unreal values, so that it can be edited by the user.
	 * This is the first step of creating a new algorithm entry.
	 * 
	 * @param uiName - Human friendly name to be used by the UI when listing services.
	 * @param wsName - The web service’s name to be used in the URL when calling the service.   e.g. "physionetAnalysisService"
	 * @param url - URL of the server containing the web services e.g. http://128.220.76.170:8080/axis2/services. <BR>
	 *        This is used together with “service.wsName” and "algorithm.method”. <BR>
	 *        e.g. http://128.220.76.170:8080/axis2/services/physionetAnalysisService/sqrsWrapperType2
	 * @return - the primary key of the new entry in the service table.
	 * @throws DataStorageException 
	 **/
	public int addNewServiceToDB(String uiName, String wsName, String url) throws DataStorageException{
		Connection dbUtility = ConnectionFactory.createConnection();

		int algorithmID = dbUtility.storeService(uiName, wsName, url);
		return algorithmID;
	}
	
	/** Updates a single new Web Service4 to the database. 
	 * 
	 * @param param - an initialized AdditionalParameters object.
	 * @param algID - Primary key of the algorithm this parameter pertains to.
	 * @return - The primary key of the new entry.
	 * @author Michael Shipway
	 * @throws DataStorageException 
	 */
	public int updateAlgorithmParameterToDB(ServiceDTO serv) throws DataStorageException{
		Connection dbUtility = ConnectionFactory.createConnection();
		
		return dbUtility.updateWebService(serv);
	}
		

	
}
