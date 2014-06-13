package com.heritrix_scheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.apache.log4j.xml.DOMConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;

public class Main {
	// VAR DECLARE
	private static HeritrixSessionImpl session;
	private static List<Event> events;
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	private static final int scanInterval = 300;
	private static final int sleepTime = 3000;//300000;
	private static ArrayList<Event> startedJobs = new ArrayList<Event>();
	private static ArrayList<Event> completedJobs= new ArrayList<Event>();
	private static boolean shouldIBeRunning = true;
	// END VAR DECLARE

	public static void main(String[] args) {
			// INIT
			buildHeritrixSession();
			GoogleCalendarHelper.init();
			// Init logging
			DOMConfigurator.configure("./log4j.xml");
			logger.debug("Logger config loaded!");
			// GoogleCalendarHelper.test();

			// Main loop
			// 11 - RED
			// 10 - Green
			// 5  - Yellow
			// 9  - Blue
			// 8  - Gray
			do {
				logger.debug("TICK");

				// Get events from gCal within the past scanInterval minutes
				refreshEvents();
				// Get gCal location and run Heritrix commands approp
				getLocationAndRunHeritrix();
				
				// Update gCal color based on Heritrix status for events jobs
				// TODO
				// TODO - add jobs heritrix says are running to started jobs, if not already there
				for (String s :session.getJobNames()) {
					for (Event p : events)
						if (s.equals(p.getSummary().toString())) {
							logger.info(p.getSummary().toString() + " - Updating gCal entry based on heritrix data");
							updateJob(p);
						}
				}
				// Update startedJobs with fresh data from gCal
				ArrayList<Event> tmp = new ArrayList<Event>();
				for (Event s : startedJobs) {
					tmp.add(GoogleCalendarHelper.getEventById(s.getId()));
				}
				startedJobs = tmp;
				// Remove completed jobs from startedJobs
				// TODO - this.. needful
				try {
				Iterator<Event> iter = startedJobs.iterator();
				while (iter.hasNext()) {
					Event s = iter.next();
				    if (s.getColorId().toString().equals("8")) {
				        iter.remove();
				    }
				}
				} catch (Exception e) {
					logger.info("We had an error removing an event from startedJobs.");
					logger.debug(e.getMessage());
				}
				

				/*
				if (startedJobs.size() >0)
					for (Event s : completedJobs)
						startedJobs.remove(startedJobs.indexOf(s));*/


				// Update gCal color based on Heritrix status for startedJobs jobs
				for (Event p : startedJobs)
					updateJob(p);
				
				// Sleepytime
				try {
				Thread.sleep(sleepTime);
				} catch (Exception e) {
					logger.info("Really? thread.sleep() failed...");
					logger.debug(e.getMessage());
				}
				// End main loop
				logger.debug("TOCK");
			} while (shouldIBeRunning);


		logger.info("END");
	}
	private static int updateJob(Event p) {
		try {
		// Get job status
		String a = refreshStatus(p.getSummary());
		if (a.equals("RUNNING"))
			GoogleCalendarHelper.setColor(p, "10");
		else if (a.equals("PAUSED")) 
			GoogleCalendarHelper.setColor(p, "5");
		else if (a.equals("FINISHED") || a.equals("Completed")) {
			GoogleCalendarHelper.setColor(p, "8");
			completedJobs.add(p);
		}
		else if (a.equals("ABORTED") || a.equals("PAUSING"))
			GoogleCalendarHelper.setColor(p, "11");
	return 0;
		} catch (Exception e) {
			logger.info("Exception in updateJob");
			logger.debug(e.getMessage());
			return -1;
		}
		
	}
	// Google calls
	private static int refreshEvents(){
		try {
		// lookahead/behind scanTime
		DateTime minTime = new DateTime(new Date(System.currentTimeMillis()
				- scanInterval * 1000));
		DateTime maxTime = new DateTime(new Date(System.currentTimeMillis()
				+ scanInterval * 1000));
		events = GoogleCalendarHelper.getEventList(minTime, maxTime);
		return 0;
		} catch (Exception e) {
			logger.info("Exception in refreshEvents");
			logger.debug(e.getMessage());	
			return -1;
		}
	}
	private static int getLocationAndRunHeritrix() {
		try {
		if (session == null) {
			logger.info("Session is null, trying to rebuild.");
			buildHeritrixSession();
		}
		if (session == null) {
			logger.info("Session could not be initialized, returning null status");
			return -1;
		}

		for (Event s : events)
		{
			// Do we need to run?
			if (s.getLocation() == null)
			{}
			// Pause
			else if(s.getLocation().toString().equalsIgnoreCase("Pause")) {
				logger.info(s.getSummary().toString() + " - Pausing job");
				session.pauseJob(s.getSummary().toString());
				GoogleCalendarHelper.setLocation(s, "");
				GoogleCalendarHelper.setColor(s, "5");
				Thread.sleep(3000);
			}
			// Unpause
			else if(s.getLocation().toString().equalsIgnoreCase("Unpause")) {
				logger.info(s.getSummary().toString() + " - Unpausing job");
				session.unpauseJob(s.getSummary().toString());
				GoogleCalendarHelper.setLocation(s, "");
				GoogleCalendarHelper.setColor(s, "10");
				Thread.sleep(3000);
			}
			// Start
			else if(s.getLocation().toString().equalsIgnoreCase("Start")) {
				logger.info(s.getSummary().toString() + " - Starting job");
				triggerJob(s.getSummary().toString());
				GoogleCalendarHelper.setLocation(s, "");
				GoogleCalendarHelper.setColor(s, "10");
				// add to started jobs array
				startedJobs.add(s);
				Thread.sleep(3000);
			}
			// Stop
			else if(s.getLocation().toString().equalsIgnoreCase("Stop")) {
				logger.info(s.getSummary().toString() + " - Stopping job");
				session.terminateJob(s.getSummary().toString());
				GoogleCalendarHelper.setLocation(s, "");
				GoogleCalendarHelper.setColor(s, "11");
				Thread.sleep(3000);
			}
		}
		return 0;
		} catch (Exception e) {
			logger.info("Exception in getLocationAndRunHeritrix");
			logger.debug(e.getMessage());
			return -1;
		}
	}

	// Heritrix calls
	private static String refreshStatus(String job) {
		try {
		if (session == null) {
			logger.info("Session is null, trying to rebuild.");
			buildHeritrixSession();
		}
		if (session == null) {
			logger.info("Session could not be initialized, returning null status");
			return null;
		}

		Document doc = session.getJobStatus(job);
		NodeList nl = doc.getElementsByTagName("statusDescription");
		String status = nl.item(0).getFirstChild().toString();
		status = status.substring(status.lastIndexOf(": ") + 2);
		status = status.substring(0, status.length() - 1);
		return status;
		} catch (Exception e) {
			logger.info("Exception in refreshStatus");;
			logger.debug(e.getMessage());
			return new String("");
		}
	}

	private static int buildHeritrixSession() {
		logger.info("Building Heritrix session...");
		// Create session
		try {
			session = new HeritrixSessionImpl(
					new File("resources/keystore.jks"), "password",
					"10.11.75.40", 82, "admin", "admin");
			logger.info("Heritrix session built!");
			return 0;
		} catch (HeritrixSessionInitializationException e) {
			logger.info("Heritrix session could not be initialized.  Please see debug logs.");
			logger.debug(e.getMessage());
			return -1;
		} catch (Exception e) {
			logger.info("Exception in buildHeritrixSession");
			logger.debug(e.getMessage());
			return -1;
		}
	}

	private static int triggerJob(String job){
		// -- Ending old job--
		// If running, stop
		try {
		if (refreshStatus(job).equals("RUNNING")) {
			session.terminateJob(job);
			logger.info(job + ": Terminated");
			Thread.sleep(3000);
		}
		// If job done/aborted, teardown
		if (refreshStatus(job).equals("ABORTED")
				|| refreshStatus(job).equals("Completed")
				|| refreshStatus(job).equals("PAUSED")
				|| refreshStatus(job).equals("PAUSING")
				|| refreshStatus(job).equals("FINISHED")) {
			session.tearDownJob(job);
			logger.info(job + ": Teardown");
			Thread.sleep(3000);
		}
		// --Starting New Job--
		// Build Job
		if (refreshStatus(job).equals("Unbuilt")) {
			session.buildJob(job);
			logger.info(job + ": Built");
			Thread.sleep(3000);
		}
		// Launch job
		if (refreshStatus(job).equals("Ready")) {
			session.launchJob(job);
			logger.info(job + ": Launched");
			Thread.sleep(3000);
		}
		// Unpause job
		if (refreshStatus(job).equals("PAUSED")) {
			session.unpauseJob(job);
			logger.info(job + ": Unpaused");
			Thread.sleep(3000);
		}

		// Check for status
		logger.info(refreshStatus(job));

		return 0;
		} catch (Exception e) {
			logger.info("Exception in triggerJob");
			logger.debug(e.getMessage());
			return -1;
		}
	}
}
