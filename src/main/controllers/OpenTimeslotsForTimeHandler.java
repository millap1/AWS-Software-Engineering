package main.controllers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.gson.Gson;

import main.database.ScheduleDAO;
import main.database.TimeslotDAO;
import main.entities.Schedule;
import main.entities.Timeslot;

/**
 * Found gson JAR file from
 * https://repo1.maven.org/maven2/com/google/code/gson/gson/2.6.2/gson-2.6.2.jar
 */
public class OpenTimeslotsForTimeHandler implements RequestStreamHandler {

	public LambdaLogger logger = null;
	String status = "OK";

	/** Load from RDS, if it exists
	 * 
	 * @throws Exception 
	 */
	
	
	@Override
	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
		logger = context.getLogger();
		logger.log("Loading Java Lambda handler to open time slots on each day for selected time");

		JSONObject headerJson = new JSONObject();
		headerJson.put("Content-Type",  "application/json");  // not sure if needed anymore?
		headerJson.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
	    headerJson.put("Access-Control-Allow-Origin",  "*");
	        
		JSONObject responseJson = new JSONObject();
		responseJson.put("headers", headerJson);

		OpenTimeslotsForTimeResponse response = null;
		
		// extract body from incoming HTTP POST request. If any error, then return 422 error
		String body;
		boolean processed = false;
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));
			JSONParser parser = new JSONParser();
			JSONObject event = (JSONObject) parser.parse(reader);
			logger.log("event:" + event.toJSONString());
			
			String method = (String) event.get("httpMethod");
			if (method != null && method.equalsIgnoreCase("OPTIONS")) {
				logger.log("Options request");
				response = new OpenTimeslotsForTimeResponse("name", 200);  // OPTIONS needs a 200 response
		        responseJson.put("body", new Gson().toJson(response));
		        processed = true;
		        body = null;
			} else {
				body = (String)event.get("body");
				if (body == null) {
					body = event.toJSONString();  // this is only here to make testing easier
				}
			}
		} catch (ParseException pe) {
			logger.log(pe.toString());
			response = new OpenTimeslotsForTimeResponse("Bad Request:" + pe.getMessage(), 422);  // unable to process input
	        responseJson.put("body", new Gson().toJson(response));
	        processed = true;
	        body = null;
		}

		if (!processed) {
			OpenTimeslotsForTimeRequest req = new Gson().fromJson(body, OpenTimeslotsForTimeRequest.class);
			logger.log(req.toString());
			status = "OK";
			
			LocalTime openTime = LocalTime.of(req.hour, req.minute);
			
			openTimeSlotsForTime(req.scheduleID, req.originizerSecretCode, openTime);
			
			//Response creation
			if(status.equals("OK")){
				response = new OpenTimeslotsForTimeResponse("Selected time slots opened successifully.");
		        responseJson.put("body", new Gson().toJson(response));
			}
			else if(status.equals("Something went wrong and request failed to exicute. Please retry")) {
				
				response = new OpenTimeslotsForTimeResponse(status, 500);
		        responseJson.put("body", new Gson().toJson(response));
			}
			else {
				response = new OpenTimeslotsForTimeResponse(status, 422);
		        responseJson.put("body", new Gson().toJson(response));
			}
		}
		
        logger.log("end result:" + responseJson.toJSONString());
        logger.log(responseJson.toJSONString());
        OutputStreamWriter writer = new OutputStreamWriter(output, "UTF-8");
        writer.write(responseJson.toJSONString());  
        writer.close();
	}
	
	
////////////////////////////////////////////////////////////////////////////////////
	
	void openTimeSlotsForTime(String scheduleID, String originizerSecretCode, LocalTime openTime) {
		TimeslotDAO timeSlotDAO = new TimeslotDAO();
		ScheduleDAO scheduleDAO = new ScheduleDAO();
		boolean timeExists = false;
		boolean worked = true;
		Schedule schedule = null;

		try {
			schedule = scheduleDAO.getSchedule(scheduleID);
		} catch (Exception e) {
			logger.log("Schedule does not exist with provided schedule ID.");
			status = "Schedule does not exist with provided schedule ID.";
		}

		if(schedule != null) {
			if(schedule.getSecretCode().equals(originizerSecretCode)) {
				try {
					List<Timeslot> timeSlots = timeSlotDAO.getAllTimeslotsWithScheduleID(scheduleID);
					List<Timeslot> corretTimeSlots = new ArrayList<Timeslot>();
					Iterator<Timeslot> timeSlotsIterator = timeSlots.iterator();
					while (timeSlotsIterator.hasNext()){
						Timeslot timeSlot = timeSlotsIterator.next();
						LocalTime currentSlotTime = timeSlot.getStartTime().toLocalTime();
						if(openTime.equals(currentSlotTime)) {
							timeExists = true;
							corretTimeSlots.add(timeSlot);
						}
					}
					if(timeExists) {
						Iterator<Timeslot> correctTimeSlotsIterator = corretTimeSlots.iterator();
						while (correctTimeSlotsIterator.hasNext() && worked){
							Timeslot timeSlot = correctTimeSlotsIterator.next();
							timeSlot.setIsOpen(true);
							worked = timeSlotDAO.updateTimeslot(timeSlot);
							if(!worked) {
								logger.log("Time slot failed to update.");
								status = "Something went wrong and request failed to exicute. Please retry";
							}
						}
					}
					else {
						logger.log("Wrong time.");
						status = "No time slots exist in the selected time frame. Please select another time frame with atleast one time slot.";
					}
				} catch (Exception e) {
					logger.log(e.getMessage());
					status = "Something went wrong and request failed to exicute. Please retry";
				}
			}
			else {
				logger.log("Secret code provided is incorrect.");
				status = "Orginizer secret code provided is not correct to complete this action. Please try again with the correct secret code.";
			}
		}
		else {
			logger.log("Schedule does not exist with provided schedule ID.");
			status = "Schedule does not exist with provided schedule ID.";
		}
	}
	
}
