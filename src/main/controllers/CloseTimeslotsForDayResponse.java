package main.controllers;

public class CloseTimeslotsForDayResponse {
	String message;
	int httpCode;
	
	public CloseTimeslotsForDayResponse (String message, int code) {
		this.message = message;
		this.httpCode = code;
	}
	
	// 200 means success
	public CloseTimeslotsForDayResponse (String message) {
		this.message = message;
		this.httpCode = 200;
	}
	
	public String toString() {
		return "Response(" + message + ")";
	}
}
