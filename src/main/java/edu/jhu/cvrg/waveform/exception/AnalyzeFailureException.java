package edu.jhu.cvrg.waveform.exception;

import org.apache.log4j.Logger;

public class AnalyzeFailureException extends Exception{

	private static final long serialVersionUID = 6794589448142833475L;
	private Logger log = Logger.getLogger(AnalyzeFailureException.class);
	
	public AnalyzeFailureException() {
		// TODO Auto-generated constructor stub
	}

	public AnalyzeFailureException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	public AnalyzeFailureException(Throwable cause) {
		super(cause);
		// TODO Auto-generated constructor stub
	}

	public AnalyzeFailureException(String message, Throwable cause) {
		super(message, cause);
		log.error(message + " - " + cause.getMessage());
	}

}
