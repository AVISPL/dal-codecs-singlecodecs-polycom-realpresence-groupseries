/*
 * Copyright (C) 2015-2019 AVI-SPL Inc. All Rights Reserved.
 */

package com.avispl.dal.communicator.polycom.groupseries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.avispl.symphony.api.dal.control.call.CallController;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.util.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;

import com.avispl.symphony.api.dal.dto.control.Protocol;
import com.avispl.symphony.api.dal.dto.control.call.CallStatus.CallStatusState;
import com.avispl.symphony.api.dal.dto.control.call.DialDevice;
import com.avispl.symphony.api.dal.dto.control.call.MuteStatus;
import com.avispl.symphony.api.dal.dto.control.call.PopupMessage;
import com.avispl.symphony.api.dal.dto.monitor.AudioChannelStats;
import com.avispl.symphony.api.dal.dto.monitor.CallStats;
import com.avispl.symphony.api.dal.dto.monitor.ContentChannelStats;
import com.avispl.symphony.api.dal.dto.monitor.EndpointStatistics;
import com.avispl.symphony.api.dal.dto.monitor.RegistrationStatus;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.VideoChannelStats;

/**
 * PolycomGroupSeriesTest - validate call statistics for Polycom Group Series
 *
 * @author Paul Maher / Symphony Dev Team created on Sep 21, 2015
 * @since 2.7
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PolycomGroupSeriesTest {
	private static final String host = "***REMOVED***";// "***REMOVED***";
	private static final String user = "admin";
	private static final String password = "1234";// "***REMOVED***";
	private static final int port = 22;
	private static final int MAX_RETRIES = 20;
	private static final long RETRY_INTERVAL_MILLISEC = 500;

	private static PolycomGroupSeries polycomGroupSeries = new PolycomGroupSeries();
	private static String callId;
	private static String farSiteDialString;
	/**
	 * within {@link PolycomGroupSeries#dial(DialDevice)} unit test, if the device is not disconnected, this field will be set to false, preventing the in-call
	 * tests to run
	 */
	private static boolean shouldExecuteTests;

	private static DialDevice getDialDevice() {
		DialDevice device = new DialDevice();
		device.setDialString("1125198839@vtc.avispl.com");
		device.setCallSpeed(Integer.valueOf(1920));
		device.setProtocol(Protocol.SIP);
		return device;
	}

	private static MuteStatus mute() throws Exception {
		polycomGroupSeries.mute();
		return pollState(() -> {
			MuteStatus s = null;
			try {
				s = polycomGroupSeries.retrieveMuteStatus();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return s;
		}, MuteStatus.Muted, MAX_RETRIES, RETRY_INTERVAL_MILLISEC);
	}

	/**
	 * Polls periodically for the expected state and waits. if the expected result is not returned, the method will return actual result
	 *
	 * @param stateSource source method for accessing actual result
	 * @param expectedState result to compare against
	 * @param maxRetries number of retries to get the expected result before giving up
	 * @param retryIntervalMilliseconds time to wait between retries
	 * @param <T> type parameter of result
	 * @return actual result
	 */
	private static <T> T pollState(Supplier<T> stateSource, T expectedState, int maxRetries, long retryIntervalMilliseconds) {
		T actual = null;
		try {
			for (int i = 0; i < maxRetries; i++) {
				actual = stateSource.get();
				if (Objects.equals(actual, expectedState)) {
					break;
				}
				Thread.sleep(retryIntervalMilliseconds);
			}
		} catch (InterruptedException e) {
			System.out.println("Can not wait, interrupted");
			e.printStackTrace();
		}
		return actual;
	}

	private static MuteStatus unmute() throws Exception {
		polycomGroupSeries.unmute();
		return pollState(() -> {
			MuteStatus s = null;
			try {
				s = polycomGroupSeries.retrieveMuteStatus();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return s;
		}, MuteStatus.Unmuted, MAX_RETRIES, RETRY_INTERVAL_MILLISEC);
	}

	/**
	 * @throws java.lang.Exception for all errors
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		polycomGroupSeries.setHost(host);
		polycomGroupSeries.setLogin(user);
		polycomGroupSeries.setPassword(password);
		polycomGroupSeries.setPort(port);
		polycomGroupSeries.init();

		farSiteDialString = "unittest_vmr@vnoc1.chicago";
	}

	/**
	 * @throws java.lang.Exception for all errors
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		polycomGroupSeries.destroy();
	}

	/**
	 * Unit test for {@link PolycomGroupSeries#getCommandSuccessList()} and {@link PolycomGroupSeries#getCommandErrorList()}
	 */
	@Test
	public void test01_CommandAndLoginList() {
		List<String> commandReturnsSuccessCopy = new ArrayList<String>();
		commandReturnsSuccessCopy.add("cs: call[2] inactive\r\r\n");
		commandReturnsSuccessCopy.add("mute near*\r\r\n");
		commandReturnsSuccessCopy.add("ccaps:*\r\r\n");
		commandReturnsSuccessCopy.add("callinfo end\r\r\n");
		commandReturnsSuccessCopy.add("Hz\r\r\n");
		commandReturnsSuccessCopy.add("rcp:*\r\r\n");
		commandReturnsSuccessCopy.add("system is not in a call\r\r\n");
		commandReturnsSuccessCopy.add("dialing manual\r\r\n");// this is a valid response then the command to dial another device was sent
		commandReturnsSuccessCopy.add("hanging up all\r\r\n");
		commandReturnsSuccessCopy.add("mute near on\r\r\n");
		commandReturnsSuccessCopy.add("mute near off\r\r\n");
		commandReturnsSuccessCopy.add("systemsetting sipregistrarserver *\r\r\n");
		commandReturnsSuccessCopy.add("gatekeeperip *\r\r\n");
		commandReturnsSuccessCopy.add("status end\r\r\n");
		commandReturnsSuccessCopy.add("hanging up video\r\r\n");
		commandReturnsSuccessCopy.add("connection * is not active\r\r\n");

		List<String> commandReturnsErrorCopy = new ArrayList<String>();
		commandReturnsErrorCopy.add("error:command not found\r\r\n");// need to check if this one without space is ever used (not used in KOP polycom)
		commandReturnsErrorCopy.add("error: command not found\r\r\n");

		commandReturnsErrorCopy.add("error: command needs more parameters to execute successfully\r\r\n");

		List<String> loginReturnsSuccessCopy = new ArrayList<String>();
		loginReturnsSuccessCopy.add("SNMP Enabled:*\r\r\n");

		List<String> loginReturnsErrorCopy = new ArrayList<String>();
		loginReturnsErrorCopy.add("password:");

		try {
			List<String> commandReturnsSuccess = polycomGroupSeries.getCommandSuccessList();
			assertNotNull("Null Command Success list returned", commandReturnsSuccess);
			assertTrue("Incorrect Command Success list returned", commandReturnsSuccess.equals(commandReturnsSuccessCopy));

			List<String> commandReturnsError = polycomGroupSeries.getCommandErrorList();
			assertNotNull("Null Command Error list returned", commandReturnsError);
			assertTrue("Incorrect Command Error list returned", commandReturnsError.equals(commandReturnsErrorCopy));

			List<String> loginReturnsSuccess = polycomGroupSeries.getLoginSuccessList();
			assertNotNull("Null Login Success list returned", loginReturnsSuccess);
			assertTrue("Incorrect login Success list returned", loginReturnsSuccess.equals(loginReturnsSuccessCopy));

			List<String> loginReturnsError = polycomGroupSeries.getLoginErrorList();
			assertNotNull("Null Login Error list returned", loginReturnsError);
			assertTrue("Incorrect login Error list returned", loginReturnsError.equals(loginReturnsErrorCopy));
		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@link PolycomGroupSeries#doneReading(String, String)}<br>
	 * Done reading tests if the response is read till the end - to avoid next command reading response from previous
	 */
	@Test
	public void test02_DoneReading() {
		// Missing characters at end of validation command string- CALLINFO ALL
		assertFalse("Error should be seen for incorrect Command Success entry", polycomGroupSeries.doneReading("callinfo all", "System is not in a c"));

		// success case for validation command string - CALLINFO ALL
		assertTrue("Error seen for correct Command Success entry", polycomGroupSeries.doneReading("callinfo all", "system is not in a call\r\r\n"));

		// Missing characters at end of validation command string- MUTE NEAR GET
		assertFalse("Error should be seen for incorrect Command Success entry", polycomGroupSeries.doneReading("mute near get", "mute near"));

		// success case for validation command string - MUTE NEAR GET
		assertTrue("Error seen for correct Command Success entry", polycomGroupSeries.doneReading("mute near get", "mute near*\r\r\n"));

		// Missing characters at end of validation command string- ADVNETSTATS
		assertFalse("Error should be seen for incorrect Command Success entry", polycomGroupSeries.doneReading("advnetstats", "ccaps:*\r"));

		// success case for validation command string - ADVNETSTATS
		assertTrue("Error seen for correct Command Success entry", polycomGroupSeries.doneReading("advnetstats", "ccaps:*\r\r\n"));

		// Missing characters at end of validation command string- NETSTATS
		assertFalse("Error should be seen for incorrect Command Success entry", polycomGroupSeries.doneReading("netstats", "rcp:*\r"));

		// success case for validation command string - NETSTATS
		assertTrue("Error seen for correct Command Success entry", polycomGroupSeries.doneReading("netstats", "rcp:*\r\r\n"));
	}

	/**
	 * Unit test for {@link PolycomGroupSeries#dial(DialDevice)}
	 */
	@Test
	public void test03_DialAnotherDevice() {
		try {
			CallStatusState callStatus = polycomGroupSeries.retrieveCallStatus(null).getCallStatusState();
			assertNotNull("Invalid call status retreived from device", callStatus);

			shouldExecuteTests = callStatus == CallStatusState.Disconnected;
			if (shouldExecuteTests) {
				callId = polycomGroupSeries.dial(getDialDevice());

				assertNotNull("Dial results should not be null or empty.", callId);

				// need to wait for connection
				CallStatusState state = pollState(() -> {
					CallStatusState s = null;
					try {
						s = polycomGroupSeries.retrieveCallStatus(callId).getCallStatusState();
					} catch (Exception e) {
						e.printStackTrace();
					}
					return s;
				}, CallStatusState.Connected, MAX_RETRIES, RETRY_INTERVAL_MILLISEC);

				assertEquals("Polycom test device was not in a call and did not return connected status after trying to connect", state,
						CallStatusState.Connected);

				// only dial if not in call
				// this is for testing reasonable time to connect so the statistics
				// may return the actual data
				// making different offset time may be used to check the statistics
				// in the moment then call was initialized but not connected yet -
				// something that is hard to achieve manually
			}
		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@link PolycomGroupSeries#getMultipleStatistics()}
	 */
	@Test
	public void test04_GetMultipleStatistics() {
		try {
			List<Statistics> statistics = polycomGroupSeries.getMultipleStatistics();
			assertNotNull("Multiple statistic is null", statistics);
			assertFalse("Multiple statistic is empty", statistics.isEmpty());
			EndpointStatistics endpointStatistics = (EndpointStatistics) statistics.get(0);
			if (null == endpointStatistics) {
				fail("Getting Call Statistics returned null");
			}

			RegistrationStatus registration = endpointStatistics.getRegistrationStatus();
			// registration status can be obtained no matter the device is in a call
			assertNotNull("The test device registration status is null", registration);

			if (endpointStatistics.isInCall()) {
				CallStats callStats = endpointStatistics.getCallStats();

				if (!StringUtils.isNullOrEmpty(callId) && shouldExecuteTests) {
					assertTrue("Call Statistics gathering did not succeed - CallID not equal to dial results", callStats.getCallId().equals(callId));
				}
				if (callStats.getCallRateRx() != null) {
					assertTrue("Call Stats receive call rate should be greater than or equal to zero", callStats.getCallRateRx().intValue() >= 0);
				}
				if (callStats.getCallRateTx() != null) {
					assertTrue("Call Stats send call rate should be greater than or equal to zero", callStats.getCallRateTx().intValue() >= 0);
				}
				if (callStats.getProtocol() != null) {
					assertTrue("Call Stats protocol should not be empty", callStats.getProtocol().length() > 0);
				}
				if (callStats.getRemoteAddress() != null) {
					assertTrue("Call Stats remote address should not be empty", callStats.getRemoteAddress().length() > 0);
				}
				if (callStats.getRequestedCallRate() != null) {
					assertTrue("Call Stats requested call rate should be greater than or equal to zero", callStats.getRequestedCallRate().intValue() >= 0);
				}
				if (callStats.getPercentPacketLossTx() != null) {
					assertTrue("Call Stats packet loss send should be greater than or equal to zero", callStats.getPercentPacketLossTx().intValue() >= 0);
				}

				AudioChannelStats audioStats = endpointStatistics.getAudioChannelStats();
				if (audioStats != null) {
					if (audioStats.getBitRateRx() != null) {
						assertTrue("Audio Stats receive bit rate should be greater than or equal to zero", audioStats.getBitRateRx().intValue() >= 0);
					}
					if (audioStats.getBitRateTx() != null) {
						assertTrue("Audio Stats send bit rate should be greater than or equal to zero", audioStats.getBitRateTx().intValue() >= 0);
					}
					if (audioStats.getCodec() != null) {
						assertTrue("Audio Stats codec should not be empty", audioStats.getCodec().length() > 0);
					}
					if (audioStats.getJitterRx() != null) {
						assertTrue("Audio Stats jitter receive rate should be greater than or equal to zero", audioStats.getJitterRx().intValue() >= 0);
					}
					if (audioStats.getJitterTx() != null) {
						assertTrue("Audio Stats jitter send rate should be greater than or equal to zero", audioStats.getJitterTx().intValue() >= 0);
					}
					if (audioStats.getPacketLossRx() != null) {
						assertTrue("Audio Stats packet loss recieve should be greater than or equal to zero", audioStats.getPacketLossRx().intValue() >= 0);
					}
					if (audioStats.getPacketLossTx() != null) {
						assertTrue("Audio Stats packet loss send should be greater than or equal to zero", audioStats.getPacketLossTx().intValue() >= 0);
					}
					if (audioStats.getMuteTx() != null) {
						assertNotNull("Mute status could not be gathered", audioStats.getMuteTx());
					}
				}

				VideoChannelStats videoStats = endpointStatistics.getVideoChannelStats();
				if (videoStats != null) {
					if (videoStats.getBitRateRx() != null) {
						assertTrue("Video Stats receive bit rate should be greater than or equal to zero", videoStats.getBitRateRx().intValue() >= 0);
					}
					if (videoStats.getBitRateTx() != null) {
						assertTrue("Video Stats send bit rate should be greater than or equal to zero", videoStats.getBitRateTx().intValue() >= 0);
					}
					if (videoStats.getCodec() != null) {
						assertTrue("Video Stats codec should not be empty", videoStats.getCodec().length() > 0);
					}
					if (videoStats.getFrameRateRx() != null) {
						assertTrue("Video Stats receive frame rate should be greater than or equal to zero", videoStats.getFrameRateRx().intValue() >= 0);
					}
					if (videoStats.getFrameRateTx() != null) {
						assertTrue("Video Stats send frame rate should be greater than or equal to zero", videoStats.getFrameRateTx().intValue() >= 0);
					}
					if (videoStats.getFrameSizeRx() != null) {
						assertTrue("Video Stats receive frame size should not be empty", videoStats.getFrameSizeRx().length() > 0);
					}
					if (videoStats.getFrameSizeTx() != null) {
						assertTrue("Video Stats send frame size should not be empty", videoStats.getFrameSizeTx().length() > 0);
					}
					if (videoStats.getJitterRx() != null) {
						assertTrue("Video Stats receive jitter rate should be greater than or equal to zero", videoStats.getJitterRx().intValue() >= 0);
					}
					if (videoStats.getJitterTx() != null) {
						assertTrue("Video Stats send jitter rate should be greater than or equal to zero", videoStats.getJitterTx().intValue() >= 0);
					}
					if (videoStats.getPacketLossRx() != null) {
						assertTrue("Video Stats receive packet loss rate should be greater than or equal to zero",
								videoStats.getPacketLossRx().intValue() >= 0);
					}
					if (videoStats.getPacketLossTx() != null) {
						assertTrue("Video Stats send packet loss rate should be greater than or equal to zero", videoStats.getPacketLossTx().intValue() >= 0);
					}
				}
			}
		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Unit test for {@link PolycomGroupSeries#sendMessage(PopupMessage)}
	 */
	@Test
	public void test05_SendPopUp() {
		if (shouldExecuteTests) {
			PopupMessage msg = new PopupMessage();
			msg.setMessage("Hello! This is a test");
			msg.setDuration(Integer.valueOf(15));
			try {
				polycomGroupSeries.sendMessage(msg);
			} catch (Exception e) {
				fail("Device Failed to execute send message command: " + e.toString());
			}
		}
	}

	@Test
	public void testRetrieveStatistics() throws Exception {
		List<Statistics> statistics = polycomGroupSeries.getMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		controllableProperty.setProperty("Camera#Pan");
		controllableProperty.setValue("0.0");
		polycomGroupSeries.controlProperty(controllableProperty);
	}

	/**
	 * Unit test for {@link PolycomGroupSeries#mute()} and {@link PolycomGroupSeries#unmute()}
	 */
	// FIX ME @Test
	public void test06_MuteFunctionality() {
		if (shouldExecuteTests) {
			try {
				MuteStatus initialState = polycomGroupSeries.retrieveMuteStatus();
				assertNotNull("Mute Status is null", initialState);
				if (initialState.equals(MuteStatus.Muted)) {
					assertNotEquals("Unmute failed", unmute(), initialState);
					assertEquals("Mute failed", mute(), initialState);
				} else {
					assertEquals("Mute failed", mute(), initialState);
					assertNotEquals("Unmute failed", unmute(), initialState);
				}
			} catch (Exception e) {
				fail(e.toString());
			}
		}
	}

	/**
	 * Unit test for {@link PolycomGroupSeries#hangup(String)}
	 */
	@Test
	public void test07_HangupCalls() {
		if (shouldExecuteTests) {
			try {
				// hang up current call
				polycomGroupSeries.hangup(callId);

				// verify hang up
				CallStatusState state = pollState(() -> {
					try {
						return polycomGroupSeries.retrieveCallStatus(callId).getCallStatusState();
					} catch (Exception e) {
						e.printStackTrace();
					}
					return null;
				}, CallStatusState.Disconnected, MAX_RETRIES, RETRY_INTERVAL_MILLISEC);

				assertEquals(String.format("Call state should be Disconnected: %s", state), CallStatusState.Disconnected, state);
			} catch (Exception e) {
				fail("Device Failed to execute Disconnect command: " + e.toString());
			}
			callId = null;
		}
	}

	/**
	 * Offline unit test for {@link PolycomGroupSeries#getMultipleStatistics()}
	 */
	@Test
	public void test08_GetMultipleStatistics() {
		try {
			String responseAdvanced = readFile("/polycom/GroupSeries500/PolycomGroupSeries500advanced.txt");
			String responseStatus = readFile("/polycom/GroupSeries500/PolycomGroupSeries500status.txt");
			String responseNetstats = readFile("/polycom/GroupSeries500/PolycomGroupSeries500netstats.txt");

			String responseCallinfoAll = "callinfo all\n" + "callinfo begin\n"
					+ "callinfo:3:Denis Obukhov:Denis.Obukhov@avispl.com:1024:connected:notmuted:incoming:videocall\n" + "callinfo end";

			String responseGatekeeperIpGet = "gatekeeperip get\n" + "gatekeeperip 172.31.254.64\n";

			String responseMuteNearGet = "mute near get\n" + "mute near on";

			String responseGetSipRegistrarServer = "systemsetting get sipregistrarserver\n" + "systemsetting sipregistrarserver 172.31.254.64";

			PolycomGroupSeries mockPolycomGroupSeries = Mockito.spy(polycomGroupSeries);

			Mockito.doReturn(responseCallinfoAll).when(mockPolycomGroupSeries).send("callinfo all");
			Mockito.doReturn(responseGetSipRegistrarServer).when(mockPolycomGroupSeries).send("systemsetting get sipregistrarserver");
			Mockito.doReturn(responseGatekeeperIpGet).when(mockPolycomGroupSeries).send("gatekeeperip get");
			Mockito.doReturn(responseMuteNearGet).when(mockPolycomGroupSeries).send("mute near get");
			Mockito.doReturn(responseStatus).when(mockPolycomGroupSeries).send("status");
			Mockito.doReturn(responseAdvanced).when(mockPolycomGroupSeries).send("advnetstats");
			Mockito.doReturn(responseNetstats).when(mockPolycomGroupSeries).send("netstats");

			List<Statistics> statistics = mockPolycomGroupSeries.getMultipleStatistics();

			assertNotNull("getMultipleStatistics is null", statistics);
			assertFalse("Multiple Statistics not empty", statistics.isEmpty());

			EndpointStatistics endpointStatistics = (EndpointStatistics) statistics.get(0);
			CallStats callStats = endpointStatistics.getCallStats();

			assertNotNull("getCallStats is null", callStats);

			assertEquals("Call Statistic: Call id", "3", callStats.getCallId());
			assertEquals("Call Statistic: Protocol", "sip", callStats.getProtocol());
			assertEquals("Call Statistic: Remote Address", "Denis.Obukhov@avispl.com", callStats.getRemoteAddress());
			assertEquals("Call Statistic: Call Rate RX", Integer.valueOf(1593), callStats.getCallRateRx());
			assertEquals("Call Statistic: Call Rate TX", Integer.valueOf(1023), callStats.getCallRateTx());
			assertEquals("Call Statistic: Percent packet loss RX", null, callStats.getPercentPacketLossRx());
			assertEquals("Call Statistic: Percent packet loss TX", Float.valueOf(0.1F), callStats.getPercentPacketLossTx());
			assertEquals("Call Statistic: Total packet loss RX", null, callStats.getTotalPacketLossRx());
			assertEquals("Call Statistic: Total packet loss TX", Integer.valueOf(5), callStats.getTotalPacketLossTx());
			assertEquals("Call Statistic: Requested Call Rate", Integer.valueOf(1024), callStats.getRequestedCallRate());

			AudioChannelStats audioChannelStats = endpointStatistics.getAudioChannelStats();

			assertNotNull("getAudioChannelStats", audioChannelStats);

			assertEquals("Audio Channel: Codec", "G.722.1", audioChannelStats.getCodec());
			assertEquals("Audio Channel: Bit Rate RX", Integer.valueOf(32), audioChannelStats.getBitRateRx());
			assertEquals("Audio Channel: Bit Rate TX", Integer.valueOf(31), audioChannelStats.getBitRateTx());
			assertEquals("Audio Channel: Jitter RX", Float.valueOf(3), audioChannelStats.getJitterRx());
			assertEquals("Audio Channel: Jitter TX", Float.valueOf(2), audioChannelStats.getJitterTx());
			assertEquals("Audio Channel: Packet Loss RX", Integer.valueOf(154), audioChannelStats.getPacketLossRx());
			assertEquals("Audio Channel: Packet Loss TX", Integer.valueOf(0), audioChannelStats.getPacketLossTx());
			assertEquals("Audio Channel: Percent Packet Loss RX", null, audioChannelStats.getPercentPacketLossRx());
			assertEquals("Audio Channel: Percent Packet Loss TX", null, audioChannelStats.getPercentPacketLossTx());
			assertEquals("Audio Channel: Mute TX", Boolean.TRUE, audioChannelStats.getMuteTx());

			VideoChannelStats videoChannelStats = endpointStatistics.getVideoChannelStats();

			assertNotNull("getVideoChannelStats is null", videoChannelStats);

			assertEquals("Video Channel: Frame Size RX", "640x480", videoChannelStats.getFrameSizeRx());
			assertEquals("Video Channel: Frame Size TX", "720p", videoChannelStats.getFrameSizeTx());
			assertEquals("Video Channel: Frame Rate RX", Float.valueOf(29), videoChannelStats.getFrameRateRx());
			assertEquals("Video Channel: Frame Rate TX", Float.valueOf(29), videoChannelStats.getFrameRateTx());
			assertEquals("Video Channel: Frame Size Height RX", Integer.valueOf(480), videoChannelStats.getFrameSizeRxHeight());
			assertEquals("Video Channel: Frame Size Width RX", Integer.valueOf(640), videoChannelStats.getFrameSizeRxWidth());
			assertEquals("Video Channel: Frame Size Height TX", Integer.valueOf(720), videoChannelStats.getFrameSizeTxHeight());
			assertEquals("Video Channel: Frame Size Width TX", Integer.valueOf(1280), videoChannelStats.getFrameSizeTxWidth());
			assertEquals("Video Channel: Codec", "H.264", videoChannelStats.getCodec());

			assertEquals("Video Channel: Bit Rate RX", Integer.valueOf(569), videoChannelStats.getBitRateRx());
			assertEquals("Video Channel: Bit Rate TX", Integer.valueOf(992), videoChannelStats.getBitRateTx());
			assertEquals("Video Channel: Jitter RX", Float.valueOf(9), videoChannelStats.getJitterRx());
			assertEquals("Video Channel: Jitter TX", Float.valueOf(11), videoChannelStats.getJitterTx());
			assertEquals("Video Channel: Packet Loss RX", Integer.valueOf(475), videoChannelStats.getPacketLossRx());
			assertEquals("Video Channel: Packet Loss TX", Integer.valueOf(5), videoChannelStats.getPacketLossTx());
			assertEquals("Video Channel: Percent Packet Loss RX", null, videoChannelStats.getPercentPacketLossRx());
			assertEquals("Video Channel: Percent Packet Loss TX", null, videoChannelStats.getPercentPacketLossTx());
			assertEquals("Video Channel: Mute TX", null, videoChannelStats.getMuteTx());

			ContentChannelStats contentChannelStats = endpointStatistics.getContentChannelStats();

			assertNotNull("getContentChannelStats is null", contentChannelStats);

			assertEquals("Content Channel: Frame Size RX", null, contentChannelStats.getFrameSizeRx());
			assertEquals("Content Channel: Frame Size TX", null, contentChannelStats.getFrameSizeTx());
			assertEquals("Content Channel: Codec", "H.264", contentChannelStats.getCodec());
			assertEquals("Content Channel: Frame Rate RX", Float.valueOf(3), contentChannelStats.getFrameRateRx());
			assertEquals("Content Channel: Frame Rate TX", null, contentChannelStats.getFrameRateTx());
			assertEquals("Content Channel: Frame Size Height RX", null, contentChannelStats.getFrameSizeRxHeight());
			assertEquals("Content Channel: Frame Size Width RX", null, contentChannelStats.getFrameSizeRxWidth());
			assertEquals("Content Channel: Frame Size Height TX", null, contentChannelStats.getFrameSizeTxHeight());
			assertEquals("Content Channel: Frame Size Width TX", null, contentChannelStats.getFrameSizeTxWidth());

			assertEquals("Content Channel: Bit Rate RX", Integer.valueOf(992), contentChannelStats.getBitRateRx());
			assertEquals("Content Channel: Bit Rate TX", null, contentChannelStats.getBitRateTx());
			assertEquals("Content Channel: Jitter RX", null, contentChannelStats.getJitterRx());
			assertEquals("Content Channel: Jitter TX", null, contentChannelStats.getJitterTx());
			assertEquals("Content Channel: Packet Loss RX", Integer.valueOf(16), contentChannelStats.getPacketLossRx());
			assertEquals("Content Channel: Packet Loss TX", null, contentChannelStats.getPacketLossTx());
			assertEquals("Content Channel: Percent Packet Loss RX", null, contentChannelStats.getPercentPacketLossRx());
			assertEquals("Content Channel: Percent Packet Loss TX", null, contentChannelStats.getPercentPacketLossTx());

			assertEquals("Content Channel: Mute TX", null, contentChannelStats.getMuteTx());
		} catch (Exception e) {
			fail(e.toString());
		}
	}

	/**
	 * Testing of dial method when sending different protocols, specifically for bug SYUS-5776
	 */
	@Ignore
	public void test01_devTest() {
		// when true instead of failing test for each we will execute all test and fail if there are any errors with any of the individual test
		boolean useCollectedResults = true;
		List<String> collectedResults = new ArrayList<>();
		// h323 e164
		DialDevice dialDevice = null;
		try {
			dialDevice = new DialDevice();
			dialDevice.setDialString("7771991015");// 7771991015 - vsx in dev
			dialDevice.setCallSpeed(Integer.valueOf(1920));
			Protocol dialDeviceProtocol = Protocol.H323;
			dialDevice.setProtocol(dialDeviceProtocol);
			String callsProtocol = dialVerifiyAndReturnProtocol(polycomGroupSeries, polycomGroupSeries, dialDevice);

			// TODO fix this behavior in device library! See bug SYUS-5772
			if (!dialDeviceProtocol.toString().equalsIgnoreCase(callsProtocol)) {
				String errorMessage = "protocol selected: h323 (e164) doesnt match what endpoint is reporting back: " + callsProtocol;
				if (useCollectedResults) {
					collectedResults.add(errorMessage);
				} else {
					fail(errorMessage);
				}
			} else {
				collectedResults.add("h323 (e164) returned CORRECT protocol");
			}
		} catch (Exception e) {
			fail("Failed to Connect to another device: " + e.toString());
		} finally {
			try {
				polycomGroupSeries.hangup(null);
				Thread.sleep(TimeUnit.SECONDS.toMillis(5));
			} catch (Exception e) {
				fail("Failed to hangup: " + e.toString());
			}
		}

		// h323 ip
		try {
			dialDevice = new DialDevice();
			dialDevice.setDialString("172.31.254.106");// 172.31.254.106 - vsx in dev
			dialDevice.setCallSpeed(Integer.valueOf(1920));
			Protocol dialDeviceProtocol = Protocol.H323;
			dialDevice.setProtocol(dialDeviceProtocol);
			String callsProtocol = dialVerifiyAndReturnProtocol(polycomGroupSeries, polycomGroupSeries, dialDevice);

			// TODO fix this behavior in device library! See bug SYUS-5772
			if (!dialDeviceProtocol.toString().equalsIgnoreCase(callsProtocol)) {
				String errorMessage = "protocol selected: h323 (ip) doesnt match what endpoint is reporting back: " + callsProtocol;
				if (useCollectedResults) {
					collectedResults.add(errorMessage);
				} else {
					fail(errorMessage);
				}
			} else {
				collectedResults.add("h323 (ip) returned CORRECT protocol");
			}
		} catch (Exception e) {
			fail("Failed to Connect to another device: " + e.toString());
		} finally {
			try {
				polycomGroupSeries.hangup(null);
				Thread.sleep(TimeUnit.SECONDS.toMillis(5));
			} catch (Exception e) {
				fail("Failed to hangup: " + e.toString());
			}
		}

		// sip uri
		try {
			dialDevice = new DialDevice();
			dialDevice.setDialString("NH-VSX8000-1@nh.vnoc1.com");// NH-VSX8000-1@nh.vnoc1.com - vsx in dev
			dialDevice.setCallSpeed(Integer.valueOf(1920));
			Protocol dialDeviceProtocol = Protocol.SIP;
			dialDevice.setProtocol(dialDeviceProtocol);
			String callsProtocol = dialVerifiyAndReturnProtocol(polycomGroupSeries, polycomGroupSeries, dialDevice);

			if (!dialDeviceProtocol.toString().equalsIgnoreCase(callsProtocol)) {
				String errorMessage = "protocol selected: sip (uri) doesnt match what endpoint is reporting back: " + callsProtocol;
				if (useCollectedResults) {
					collectedResults.add(errorMessage);
				} else {
					fail(errorMessage);
				}
			} else {
				collectedResults.add("sip (uri) returned CORRECT protocol");
			}
		} catch (Exception e) {
			fail("Failed to Connect to another device: " + e.toString());
		} finally {
			try {
				polycomGroupSeries.hangup(null);
				Thread.sleep(TimeUnit.SECONDS.toMillis(5));
			} catch (Exception e) {
				fail("Failed to hangup: " + e.toString());
			}
		}

		// sip ip
		try {
			dialDevice = new DialDevice();
			dialDevice.setDialString("172.31.254.106");
			dialDevice.setCallSpeed(Integer.valueOf(1920));
			Protocol dialDeviceProtocol = Protocol.SIP;
			dialDevice.setProtocol(dialDeviceProtocol);
			String callsProtocol = dialVerifiyAndReturnProtocol(polycomGroupSeries, polycomGroupSeries, dialDevice);

			if (!dialDeviceProtocol.toString().equalsIgnoreCase(callsProtocol)) {
				String errorMessage = "protocol selected: sip (ip) doesnt match what endpoint is reporting back: " + callsProtocol;
				if (useCollectedResults) {
					collectedResults.add(errorMessage);
				} else {
					fail(errorMessage);
				}
			} else {
				collectedResults.add("sip (ip) returned CORRECT protocol");
			}
		} catch (Exception e) {
			fail("Failed to Connect to another device: " + e.toString());
		} finally {
			try {
				polycomGroupSeries.hangup(null);
				Thread.sleep(TimeUnit.SECONDS.toMillis(5));
			} catch (Exception e) {
				fail("Failed to hangup: " + e.toString());
			}
		}

		// isdn e164
		try {
			dialDevice = new DialDevice();
			dialDevice.setDialString("7771991015");
			dialDevice.setCallSpeed(Integer.valueOf(1920));
			Protocol dialDeviceProtocol = Protocol.ISDN;
			dialDevice.setProtocol(dialDeviceProtocol);

			// TODO fix this behavior in device library! See bug SYUS-5772
			String callsProtocol = dialVerifiyAndReturnProtocol(polycomGroupSeries, polycomGroupSeries, dialDevice);

			if (!dialDeviceProtocol.toString().equalsIgnoreCase(callsProtocol)) {
				String errorMessage = "protocol selected: isdn (e164) doesnt match what endpoint is reporting back: " + callsProtocol;
				if (useCollectedResults) {
					collectedResults.add(errorMessage);
				} else {
					fail(errorMessage);
				}
			} else {
				collectedResults.add("isdn (e164) returned CORRECT protocol");
			}
		} catch (Exception e) {
			fail("Failed to Connect to another device: " + e.toString());
		} finally {
			try {
				polycomGroupSeries.hangup(null);
			} catch (Exception e) {
				fail("Failed to hangup: " + e.toString());
			}
		}

		if (useCollectedResults && !collectedResults.isEmpty()) {
			StringBuilder message = new StringBuilder("\n");
			for (String collectedResult : collectedResults) {
				message.append(collectedResult + "\n");
			}
			fail(message.toString());
		}
	}


	/**
	 * dials another device, waits 30 seconds, then gets call stats from it and returns the protocol of the call
	 *
	 * @param dialingDevice used to get call status, and to issue dial command
	 * @param dialingDeviceForMonitoring used to get statistics from the device in order to get protocol of the call (should be same device as dialingDevice)
	 * @param dialDevice this defines host, protocol and speed to dial to
	 * @return protocol string of the call
	 * @throws Exception if any errors
	 */
	public static String dialVerifiyAndReturnProtocol(CallController dialingDevice, Monitorable dialingDeviceForMonitoring, DialDevice dialDevice) throws Exception {
		CallStatusState callStatus = dialingDevice.retrieveCallStatus(null).getCallStatusState();
		if (null == callStatus || callStatus != CallStatusState.Disconnected) {
			throw new Exception("dialer device is already in a call cannot dial out." + callStatus);
		}

		dialingDevice.hangup(null);

		Thread.sleep(2000); // delay for a call to be hungup

		String dialResult = dialingDevice.dial(dialDevice);

		Thread.sleep(TimeUnit.SECONDS.toMillis(20));

		callStatus = dialingDevice.retrieveCallStatus(dialResult).getCallStatusState();
		if (callStatus != CallStatusState.Connected) {
			throw new Exception("dialer device was not in a call after connection attempt.");
		}

		EndpointStatistics endpointStatistics = (EndpointStatistics) dialingDeviceForMonitoring.getMultipleStatistics().get(0);
		CallStats callStats = endpointStatistics.getCallStats();
		String protocol = callStats.getProtocol();
		if (StringUtils.isNullOrEmpty(protocol)) {
			throw new Exception("null protocol for the call retreived from device");
		}
		return protocol;
	}

	private String readFile(String name) throws Exception {
		return new String(Files.readAllBytes(Paths.get(this.getClass().getResource(name).toURI())));
	}

	// /**
	// * Test method for {@link com.avispl.symphony.communicator.polycom.PolycomGroupSeries#retrieveRegistrationStatus()}.
	// */
	// // TODO fix and uncomment @Test
	// public void test09_RetrieveRegistrationStatus() {
	// try {
	// RegistrationStatus registrationStatus = polycomGroupSeries.retrieveRegistrationStatus();
	// assertNotNull("registrationStatus shouldnt be null", registrationStatus);
	// } catch (Exception e) {
	// fail("retrieveRegistrationStatus failed with error: " + e.toString());
	// }
	// }
}
