package main.controllers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.google.gson.Gson;

import main.database.ScheduleDAO;
import main.database.TimeslotDAO;
import main.entities.*;

/**
 * Found gson JAR file from
 * https://repo1.maven.org/maven2/com/google/code/gson/gson/2.6.2/gson-2.6.2.jar
 */
public class CreateScheduleHandler implements RequestStreamHandler {

	public LambdaLogger logger = null;

	/** Load from RDS, if it exists
	 * 
	 * @throws Exception 
	 */
	/*
	 * public Schedule(String scheduleName, String scheduleID, String secretCode, Date startDate, Date endDate,
						Time dayStartTime, Time dayEndTime, int timeSlotDuration)
	{
	 */
	

	
	@Override
	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
		logger = context.getLogger();
		logger.log("Loading Java Lambda handler to create constant");

		JSONObject headerJson = new JSONObject();
		headerJson.put("Content-Type",  "application/json");  // not sure if needed anymore?
		headerJson.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
	    headerJson.put("Access-Control-Allow-Origin",  "*");
	        
		JSONObject responseJson = new JSONObject();
		responseJson.put("headers", headerJson);

		CreateScheduleResponse response = null;
		
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
				response = new CreateScheduleResponse("name", 200);  // OPTIONS needs a 200 response
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
			response = new CreateScheduleResponse("Bad Request:" + pe.getMessage(), 422);  // unable to process input
	        responseJson.put("body", new Gson().toJson(response));
	        processed = true;
	        body = null;
		}

		if (!processed) {
			CreateScheduleRequest req = new Gson().fromJson(body, CreateScheduleRequest.class);
			logger.log(req.toString());

			/*
			 * From HTML
			 * data["scheduleName"] = arg1;
			 * data["startDate"] = arg2;
			 * data["endDate"] = arg3;
			 * data["startTime"] = arg4;
			 * data["endTime"] = arg5;
			 * data["increment"] = arg6;
			 */

			Schedule newSchedule = new Schedule(req.scheduleName, req.startDate, req.endDate, req.startTime, req.endTime, req.increment);
			String ID = newSchedule.getScheduleID();
			String key = newSchedule.getSecretCode();

			// compute proper response
			CreateScheduleResponse resp = new CreateScheduleResponse("OK", ID, key);
	        responseJson.put("body", new Gson().toJson(resp));  
		}
		
        logger.log("end result:" + responseJson.toJSONString());
        logger.log(responseJson.toJSONString());
        OutputStreamWriter writer = new OutputStreamWriter(output, "UTF-8");
        writer.write(responseJson.toJSONString());  
        writer.close();
	}
	
	
///////////////////////////////// Milap Code edition Working 
	
	boolean createSchedule(String scheduleName, String startDate, String endDate, int dayStartTime, int dayEndTime, int timeSlotDuration) throws Exception {
		if (logger != null) { logger.log("in createConstant"); }
		ScheduleDAO dao = new ScheduleDAO();
		
		// check if present
		//ScheduleDAO exist = dao.getConstant(name);
		
		
		
		//	public Schedule(String scheduleName, String startDate, String endDate, int dayStartTime, int dayEndTime, int timeSlotDuration)
		Schedule schedule = new Schedule(scheduleName, startDate, endDate, dayStartTime, dayEndTime, timeSlotDuration);
		boolean ans = dao.addSchedule(schedule);
		//createTimeslots(schedule.getScheduleID(),  schedule.getStartDate(), schedule.getEndDate(), schedule.getDayStartTime(), schedule.getDayEndTime(), schedule.getTimeSlotDuration());
		
		return ans;
	}
	
	
	
	void createTimeslots(String scheduleID, LocalDate startDate, LocalDate endDate, int startTime, int endTime, int duration) {
		TimeslotDAO tdao = new TimeslotDAO(); 
		
		long dailyTime = (endTime - startTime)*60;
		long numTimeslotsPerDay = dailyTime/duration;
		long numDays= ChronoUnit.DAYS.between(startDate, endDate);
		
		LocalDate itterationDate = startDate;
		LocalTime sTime = LocalTime.of(startTime, 0);
		
		for (int i = 0; i < (int) numDays; i++)
		{
			if (itterationDate.getDayOfWeek().name() == "SATURDAY" || itterationDate.getDayOfWeek().name() == "SUNDAY")
				itterationDate = itterationDate.plusDays(1);
			
			else {
				for (long j = 0; j < numTimeslotsPerDay; j++)
				{
					Timeslot ts = new Timeslot(scheduleID, itterationDate, LocalDateTime.of(itterationDate, sTime), false, true);
					try {
						System.out.println("datetime: " + LocalDateTime.of(itterationDate, sTime));
						boolean ans = tdao.addTimeslot(ts);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					sTime = sTime.plusMinutes(duration);
				}	
				itterationDate = itterationDate.plusDays(1);
			}
		}
		
	}
	

	
}