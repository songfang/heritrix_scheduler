package com.heritrix_scheduler;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.log4j.xml.DOMConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;

@SuppressWarnings("deprecation")
public class GoogleCalendarHelper {
	private static Calendar client;
	private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarHelper.class);


	public static void test() {
		// lookahead/behind 5min
		DateTime minTime = new DateTime(new Date(
					System.currentTimeMillis() - 300 * 1000));
		DateTime maxTime = new DateTime(new Date(
					System.currentTimeMillis() + 300 * 1000));
		logger.debug("Setting lookahead/behind" + minTime.toString() + " -> " + maxTime.toString());

		try {
			for (Event event : getEventList(minTime, maxTime)) {
				logger.debug(event.getId() + ": " + event.getSummary() + " " + event.getDescription());
			}
			} catch (Exception e) {
				logger.info("Exception in test..likely");
				logger.debug(e.getMessage());
			}
	}

	public static void init() {
		// Init loggin
		DOMConfigurator.configure("./log4j.xml");

		try {
		GoogleCredential credentials = new GoogleCredential.Builder()
					.setTransport(GoogleNetHttpTransport.newTrustedTransport())
					.setJsonFactory(new GsonFactory())
					.setServiceAccountId(
						"285001760835-6obkg70r3bgk8mujca3hdif4pvm9ssnv@developer.gserviceaccount.com")
					.setServiceAccountScopes(
						Arrays.asList("https://www.googleapis.com/auth/calendar"))
					.setServiceAccountPrivateKeyFromP12File(
						new File(
							"resources/a8e3ad8111ef7206ab7cef093c1bb4e4b2301113-privatekey.p12"))
					.build();
				client = new Calendar.Builder(
						GoogleNetHttpTransport.newTrustedTransport(),
						new GsonFactory(), credentials).setApplicationName("gcalget")
					.build();
				logger.debug("Google calendar opened!");
		} catch (GeneralSecurityException e) {
			logger.info("GeneralSecurityException in init.");
			logger.debug(e.getMessage());
		} catch (IOException e) {
			logger.info("IOException in init.");
			logger.debug(e.getMessage());
		} catch (Exception e) {
			logger.info("Exception in init.");
			logger.debug(e.getMessage());
		}
	}

	public static List<Event> getEventList(DateTime minTime, DateTime maxTime)
	{
		String pageToken = null;
		List<Event> items;
		try {
			do {
				Events events = client.events().list("primary")
					.setFields("items(id, creator, summary, description, location, colorId, start(dateTime), end(dateTime))")
					.setPageToken(pageToken).setTimeMin(minTime)
					.setTimeMax(maxTime).execute();
				items = events.getItems();
				pageToken = events.getNextPageToken();
			} while (pageToken != null);
			if (items != null)
				return items;
		} catch (IOException e) {
			logger.info("IOException oin getEventList");
			logger.debug(e.getMessage());
		} catch (Exception e) {
			logger.info("Exception in getEventList");
			logger.debug(e.getMessage());
		}
		return null;
	}

	public static Event getEventById(String id) {
		try {
			return client.events().get("primary", id).execute();
		} catch (IOException e) {
			logger.info("IOException in getEventById");
			logger.debug(e.getMessage());
		}
		return null;
	}

	public static void setColor(Event event, String colorID) {
		try {
			logger.debug(event.getSummary().toString() + " - Setting color to: " + colorID);
			Event evnt = getEventById(event.getId());			
			evnt.setColorId(colorID);
			client.events().update("primary", evnt.getId(), evnt).execute();

		} catch (IOException e) {
			logger.info(event.getSummary().toString() + " - ERROR setting color");
			logger.debug(e.toString());
		}
	}

	public static void setLocation (Event event, String text) {
		try {
			logger.debug(event.getLocation().toString() + " - Setting location to: " + text);
			Event evnt = getEventById(event.getId());			
			evnt.setLocation(text);
			client.events().update("primary", evnt.getId(), evnt).execute();

		} catch (IOException e) {
			logger.info(event.getSummary().toString() + " - ERROR setting location");
			logger.debug(e.toString());
			e.printStackTrace();
		} catch (Exception e) {
			logger.info("Exception in setColor");
			logger.debug(e.getMessage());
		}
	}

	public static int appendDescription(Event event, String text) {
		try {
			logger.debug(event.getSummary().toString() + " - Appending description: " + text);
			String prevDescription;
			prevDescription = event.getDescription();
			if (prevDescription == null)
				event.setDescription(new Date().toLocaleString() + " - " + text);
			else
				event.setDescription(prevDescription + "\n"
						+ new Date().toLocaleString() + " - " + text);

			client.events().update("primary", event.getId(), event).execute();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}
}
