/*
 * Copyright (c) 2015-2021 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.dal.communicator.polycom.groupseries;

import com.avispl.dal.communicator.polycom.groupseries.utils.StringUtils;
import com.avispl.symphony.api.common.error.NotImplementedException;
import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.control.call.CallController;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.control.Protocol;
import com.avispl.symphony.api.dal.dto.control.call.CallStatus;
import com.avispl.symphony.api.dal.dto.control.call.CallStatus.CallStatusState;
import com.avispl.symphony.api.dal.dto.control.call.DialDevice;
import com.avispl.symphony.api.dal.dto.control.call.MuteStatus;
import com.avispl.symphony.api.dal.dto.control.call.PopupMessage;
import com.avispl.symphony.api.dal.dto.monitor.*;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.communicator.SshCommunicator;
import org.springframework.util.CollectionUtils;

import javax.security.auth.login.FailedLoginException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.avispl.dal.communicator.polycom.groupseries.utils.StringUtils.convertToFloat;
import static com.avispl.dal.communicator.polycom.groupseries.utils.StringUtils.convertToInteger;
import static java.util.Collections.singletonList;
import static java.util.Objects.nonNull;

/**
 * This class handles all communications to and from a Polycom Group Series device.
 *
 * @author OwenGerig Created on July 15, 2015
 * @since 2.7
 */

public class PolycomGroupSeries extends SshCommunicator implements CallController, Monitorable, Controller {
    /*
     *	See https://support.polycom.com/content/dam/polycom-support/products/telepresence-and-video/g7500/user/en/g7500-command-line-api-reference-guide.pdf
     *	for tokens details
     */
    private static final String REGEX_REMOVE_ALL_ALPHABETIC_CHARACTERS = "[^\\d.]";
    private static final String HANGUP_ALL = "hangup all";
    private static final String LOWER_CASE_P = "p";
    private static final String GATEKEEPERIP_GET = "gatekeeperip get";
    private static final String SYSTEMSETTING_GET_SIPREGISTRARSERVER = "systemsetting get sipregistrarserver";
    private static final String MUTE_NEAR_ON = "mute near on";
    private static final String MUTE_NEAR_OFF = "mute near off";
    private static final String GET_CALL_STATE = "callinfo all";
    private static final String GET_MUTE_STATUS = "mute near get";
    private static final String MUTE_STATUS = MUTE_NEAR_ON;
    private static final String GET_ADVANCED_STATS = "advnetstats";
    private static final String GET_NETWORK_STATS = "netstats";
    private static final String VIDEO_CODEC_CODE = "tvp";
    private static final String AUDIO_CODEC_CODE = "tap";
    private static final String PROTOCOL_CODE = "tcp";

    private static final String STATUS = "status";
    private static final String WHOAMI = "whoami";

    private static final String VOLUME = "volume %s";
    private static final String GET = "get";
    private static final String SET = "set ";

    private static final String VIDEOMUTE = "videomute near %s";

    private static final String CAMERA_INVERT_NEAR = "camerainvert near %s"; //get | on | off
    private static final String CAMERA_NEAR_TRACKING = "camera near tracking %s"; //get | on | off
    private static final String CAMERA_NEAR_TRACKING_CALIBRATE = "cameratracking near calibrate %s"; //get | on | off
    private static final String CAMERA_NEAR_TRACKING_FRAMING = "cameratracking near framing %s"; //get | wide | medium | tight
    private static final String CAMERA_NEAR_TRACKING_MODE = "cameratracking near mode %s"; //get | off | group | speaker | groupwithtransition
    private static final String CAMERA_NEAR_TRACKING_PARTICIPANT = "cameratracking near participant %s"; //get | off |on
    private static final String CAMERA_NEAR_TRACKING_PIP = "cameratracking near pip %s"; //get | off |on
    private static final String CAMERA_NEAR_TRACKING_SPEED = "cameratracking near speed %s"; //get | slow | normal | fast
    private static final String CAMERA_NEAR_TRACKING_WAKE = "cameratracking near wake %s"; //get | on | off
    private static final String CAMERA_NEAR_SETPOSITION = "camera near setposition %s %s %s";
    private static final String CAMERA_NEAR_GETPOSITION = "camera near getposition";

    private static final String AUDIO_LABEL_VOLUME = "Audio#Volume";
    private static final String AUDIO_LABEL_MUTE = "Audio#MuteMicrophones";
    private static final String CAMERA_LABEL_PAN = "Camera#CameraPan";
    private static final String CAMERA_LABEL_TILT = "Camera#CameraTilt";
    private static final String CAMERA_LABEL_ZOOM = "Camera#CameraZoom";
    private static final String CAMERA_LABEL_MUTE = "Camera#Mute";
    private static final String CAMERA_LABEL_INVERT = "Camera#Invert";
    private static final String CAMERA_LABEL_TRACKING = "Camera#Tracking";
    private static final String CAMERA_LABEL_TRACKING_CALIBRATE = "Camera#TrackingCalibrate";
    private static final String CAMERA_LABEL_TRACKING_FRAMING = "Camera#TrackingFraming";
    private static final String CAMERA_LABEL_TRACKING_MODE = "Camera#TrackingMode";
    private static final String CAMERA_LABEL_TRACKING_PARTICIPANT = "Camera#TrackingParticipant";
    private static final String CAMERA_LABEL_TRACKING_PIP = "Camera#TrackingPIP";
    private static final String CAMERA_LABEL_TRACKING_WAKE = "Camera#TrackingWake";
    private static final String CAMERA_LABEL_TRACKING_SPEED = "Camera#TrackingSpeed";

    private static final String AUDIO_TX_RATE_CODE = "tar";
    private static final String AUDIO_RX_RATE_CODE = "rar";
    private static final String VIDEO_RX_RATE_CODE = "rvr";
    private static final String VIDEO_TX_RATE_CODE = "tvr";
    private static final String AUDIO_TX_JITER_CODE = "taj";
    private static final String AUDIO_RX_JITTER_CODE = "raj";
    private static final String AUDIO_TX_PACKETLOSS_CODE = "tapl";
    private static final String AUDIO_RX_PACKETLOSS_CODE = "rapl";
    private static final String CONNECTED = "connected";
    private static final String VIDEO_TX_JITTER_CODE = "tvj";
    private static final String VIDEO_RX_JITTER_CODE = "rvj";
    private static final String VIDEO_TX_PACKETLOSS_CODE = "tvpl";
    private static final String VIDEO_RX_PACKETLOSS_CODE = "rvpl";
    private static final String VIDEO_TX_BITRATE_CODE = "tvru";
    private static final String VIDEO_RX_BITRATE_CODE = "rvru";
    private static final String VIDEO_TX_FRAMERATE_CODE = "tvfr";
    private static final String VIDEO_RX_FRAMERATE_CODE = "rvfr";
    private static final String VIDEO_TX_FRAME_SIZE_CODE = "tvf";
    private static final String VIDEO_RX_FRAME_SIZE_CODE = "rvf";

    private static final String CONTENT_TX_RATE_CODE = "tcr";
    private static final String CONTENT_RX_RATE_CODE = "rcr";
    private static final String CONTENT_TX_RATE_USED_CODE = "tcru";
    private static final String CONTENT_RX_RATE_USED_CODE = "rcru";
    private static final String CONTENT_TX_FRAMERATE_CODE = "tcfr";
    private static final String CONTENT_RX_FRAMERATE_CODE = "rcfr";
    private static final String CONTENT_TX_PACKETLOSS_CODE = "tcpl";
    private static final String CONTENT_RX_PACKETLOSS_CODE = "rcpl";
    private static final String CONTENT_RX_CODEC_CODE = "rctp";

    private static final String TOTAL_TX_PACKETLOSS_CODE = "pktloss";
    private static final String PERCENT_TX_PACKETLOSS_CODE = "%pktloss";
    private static final String TOKEN_SEPERATOR = ":";
    private static final String WILDCARD_TOKEN = "*";
    private static final String WILDCARD_CHARACTER = "\\*";
    private static final String LINE_BREAKER = "\r\r\n";
    private static final String X_CHARACTER = "x";
    private static final String NULL_STATISTIC = "---";
    private static final int MAX_STATUS_POLL_ATTEMPT = 20; // TODO extract into configurable property
    private static final int RETRY_INTERVAL_MILLISEC = 1000; // TODO extract into configurable property

    private static void cleanDisabledStats(ContentChannelStats stats) {
        Float frameRateRx = stats.getFrameRateRx();
        String frameSizeRx = stats.getFrameSizeRx();
        Integer bitRateRx = stats.getBitRateRx();
        if ((null == frameRateRx || frameRateRx.floatValue() == 0.0) && (null == frameSizeRx || Objects.equals(frameSizeRx, NULL_STATISTIC))
                && (null == bitRateRx || bitRateRx.intValue() == 0)) {

            stats.setFrameRateRx(null);
            stats.setBitRateRx(null);
            stats.setPacketLossRx(null);
        }

        Float frameRateTx = stats.getFrameRateTx();
        String frameSizeTx = stats.getFrameSizeTx();
        Integer bitRateTx = stats.getBitRateTx();
        if ((null == frameRateTx || frameRateTx.floatValue() == 0.0) && (null == frameSizeTx || Objects.equals(frameSizeTx, NULL_STATISTIC))
                && (null == bitRateTx || bitRateTx.intValue() == 0)) {

            stats.setFrameRateTx(null);
            stats.setBitRateTx(null);
            stats.setPacketLossTx(null);
        }
    }

    private static boolean isNotEmpty(VideoChannelStats videoChannelStats) {
        return !Objects.equals(videoChannelStats.getCodec(), NULL_STATISTIC)
                || Optional.ofNullable(videoChannelStats.getBitRateTx()).orElse(0) != 0
                || Optional.ofNullable(videoChannelStats.getBitRateRx()).orElse(0) != 0
                || Optional.ofNullable(videoChannelStats.getJitterRx()).orElse(0F) != 0
                || Optional.ofNullable(videoChannelStats.getJitterTx()).orElse(0F) != 0
                || Optional.ofNullable(videoChannelStats.getPacketLossRx()).orElse(0) != 0
                || Optional.ofNullable(videoChannelStats.getPacketLossTx()).orElse(0) != 0
                || Optional.ofNullable(videoChannelStats.getFrameRateRx()).orElse(0F) != 0
                || Optional.ofNullable(videoChannelStats.getFrameRateTx()).orElse(0F) != 0
                || (videoChannelStats.getFrameSizeRx() != null && !Objects.equals(videoChannelStats.getFrameSizeRx(), NULL_STATISTIC))
                || (videoChannelStats.getFrameSizeRx() != null && !Objects.equals(videoChannelStats.getFrameSizeTx(), NULL_STATISTIC));
    }

    /**
     * {@inheritDoc}
     * <p>
     * commandSuccessList and commandErrorList may contain an entry that has a wildcard. This Override method has been built to look for this wildcard
     * character. If a wildcard is found, split the entry and verify all segments are found (the last segment should match the end of the response string).
     */
    @Override
    protected boolean doneReading(String command, String response) throws CommandFailureException {
        for (String string : commandErrorList) {
            if (string.contains(WILDCARD_TOKEN)) {
                if (allSegmentsFound(response, string)) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Done reading, found error string: " + string + " from: " + host + " port: " + port);
                    }
                    throw new CommandFailureException(host, command, response);
                }

            } else if (response.endsWith(string)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Done reading, found error string: " + string + " from: " + host + " port: " + port);
                }
                throw new CommandFailureException(host, command, response);
            }
        }

        for (String string : commandSuccessList) {
            if (string.contains(WILDCARD_TOKEN)) {
                if (allSegmentsFound(response, string)) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Done reading, found success string: " + string + " from: " + host + " port: " + port);
                    }
                    return true;
                }

            } else if (response.endsWith(string)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Done reading, found success string: " + string + " from: " + host + " port: " + port);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * loginSuccessList and loginErrorList may contain an entry that has a wildcard. This Override method has been built to look for this wildcard character. If
     * a wildcard is found, split the entry and verify all segments are found (the last segment should match the end of the response string).
     */
    @Override
    protected boolean doneReadingAfterConnect(String response) throws FailedLoginException {
        // loop through loginErrorList and see if any of the errors are seen during login.
        for (String string : loginErrorList) {
            if (string.contains(WILDCARD_TOKEN)) {

                if (allSegmentsFound(response, string)) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Done reading, found error string: " + string + " from: " + host + " port: " + port);
                    }
                    throw new FailedLoginException("Wildcard Login failed: " + response);
                }
            } else if (response.endsWith(string)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Done reading, found error string: " + string + " from: " + host + " port: " + port);
                }
                throw new FailedLoginException("Login failed: " + response);
            }
        }

        // loop through loginSuccessList and see if any of the success messages are seen during login.
        for (String string : loginSuccessList) {
            if (string.contains(WILDCARD_TOKEN)) {
                // all segments for an entry in the loginSuccessList has been found. Login success.
                if (allSegmentsFound(response, string)) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Done reading, found success string: " + string + " from: " + host + " port: " + port);
                    }
                    return true;
                }
            } else if (response.endsWith(string)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Done reading, found success string: " + string + " from: " + host + " port: " + port);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Take the parameter string and split it by our WILDCARD_CHARACTER "*". Check that our response ends with the last item in the array [created via the
     * split] then check if all other items in the array are also contained within the response parameter.
     */
    private static boolean allSegmentsFound(String response, String string) {
        String[] parseCommand = string.split(WILDCARD_CHARACTER);
        int commandLength = parseCommand.length - 1;
        String lastSegment = parseCommand[commandLength];
        if (response.endsWith(lastSegment)) {
            for (int i = 0; i < commandLength; i++) {
                if (!response.contains(parseCommand[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Our class must know the expected response in order to communicate properly with the Polycom Group Series. So whenever adding new commands its imported to
     * update this method with the expected responses.
     */
    private void intializeCommunicationLists() {
        // NOTE: some data is returned in multiple lines, therefore looking for \r\r\n will not accurately get all data
        // The code will use a Wildcard character '*' to look for strings near end of input
        commandSuccessList = new ArrayList<String>();
        commandSuccessList.add("cs: call[2] inactive\r\r\n");
        commandSuccessList.add("mute near*\r\r\n");
        commandSuccessList.add("ccaps:*\r\r\n");
        commandSuccessList.add("callinfo end\r\r\n");
        commandSuccessList.add("Hz\r\r\n");
        commandSuccessList.add("rcp:*\r\r\n");
        commandSuccessList.add("system is not in a call\r\r\n");
        commandSuccessList.add("dialing manual\r\r\n");// this is a valid response then the command to dial another device was sent
        commandSuccessList.add("hanging up all\r\r\n");
        commandSuccessList.add("mute near on\r\r\n");
        commandSuccessList.add("mute near off\r\r\n");
        commandSuccessList.add("systemsetting sipregistrarserver *\r\r\n");
        commandSuccessList.add("gatekeeperip *\r\r\n");
        commandSuccessList.add("status end\r\r\n");
        commandSuccessList.add("hanging up video\r\r\n");
        commandSuccessList.add("connection * is not active\r\r\n");
        commandSuccessList.add("Minutes\r\r\n");
        commandSuccessList.add("Days\r\r\n");
        commandSuccessList.add("Hours\r\r\n");
        commandSuccessList.add(":*\r\r\n");
        commandSuccessList.add("volume *\r\r\n");
        commandSuccessList.add("camera*\r\r\n");

        setCommandSuccessList(commandSuccessList);

        commandErrorList = new ArrayList<String>();
        commandErrorList.add("error:command not found\r\r\n");
        commandErrorList.add("error: command not found\r\r\n");
        commandErrorList.add("error: command needs more parameters to execute successfully\r\r\n");
        setCommandErrorList(commandErrorList);

        loginSuccessList = new ArrayList<String>();
        loginSuccessList.add("SNMP Enabled:*\r\r\n");
        setLoginSuccessList(loginSuccessList);

        loginErrorList = new ArrayList<String>();
        loginErrorList.add("password:");
        setLoginErrorList(loginErrorList);
    }

    /**
     * Polycom Group Series constructor
     */
    public PolycomGroupSeries() {
        super();
        intializeCommunicationLists();
    }

    @Override
    protected void internalInit() throws Exception {
        super.internalInit();
    }

    /**
     * {@inheritDoc} <br>
     *
     * <pre>
     * PolycomGroupSeries command getcallstate returns three call stats.
     * 		cs: call[0] inactive
     * 		cs: call[1] inactive
     * 		cs: call[2] inactive
     * After research and testing, it appears only the top call stat is changed.  The number in the brackets [0] varies, but third line never changes.
     *
     * Note: The Polycom Group Series does not report on Percent Packet Loss receive.
     * The following text comes from the Polycom Group Series guide:
     * 		Both pktloss and %pktloss report only numbers related to packet loss on the transmit. These numbers are
     * 		not affected by packet loss on the Real-time Transport Protocol (RTP) that is received.
     * 		The number listed for %pktloss is not cumulative and is calculated every 5 seconds. The number listed for
     * 		pktloss is calculated every 5 seconds and is cumulative.
     * </pre>
     */
    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        EndpointStatistics statistics = new EndpointStatistics();
        ExtendedStatistics extendedStatistics = new ExtendedStatistics();
        Map<String, String> extendedStatisticsData = new HashMap<>();
        List<AdvancedControllableProperty> advancedControllableProperties = new ArrayList<>();

        String deviceStatus = retrieveStatus();

        extractDeviceStatus(extendedStatisticsData, deviceStatus);
        populateDeviceData(extendedStatisticsData);

        populateAudioData(extendedStatisticsData, advancedControllableProperties);

        extendedStatistics.setStatistics(extendedStatisticsData);
        extendedStatistics.setControllableProperties(advancedControllableProperties);

        CallStats callStats = null;

        // Add code to return registration status
        RegistrationStatus registrationStats = extractRegistrationStatus(deviceStatus);
        statistics.setRegistrationStatus(registrationStats);

        String[] activeCallStatus = retrieveRawCallStatistics();
        if (null == activeCallStatus) {
            statistics.setInCall(false);
            return Arrays.asList(statistics, extendedStatistics);
        }

        statistics.setInCall(true);
        populateCameraData(extendedStatisticsData, advancedControllableProperties);
        callStats = parseCallIdAndRemoteAddress(activeCallStatus);
        callStats.setRequestedCallRate(convertToInteger(activeCallStatus[4]));

        AudioChannelStats audioChannelStats = new AudioChannelStats();
        VideoChannelStats videoChannelStats = new VideoChannelStats();
        ContentChannelStats contentChannelStats = new ContentChannelStats();

        audioChannelStats.setMuteTx(send(GET_MUTE_STATUS).contains(MUTE_STATUS));

        String networkStats = send(GET_NETWORK_STATS);
        if (networkStats != null && networkStats.length() > 0) {
            // StringTokenizer will create an "array" that is split on the Space character and End Of Line characters.
            // We can loop through the array via "hasMoreTokens". If the entry contains a ":" character, it contains data
            StringTokenizer networkTokenizer = new StringTokenizer(networkStats);
            while (networkTokenizer.hasMoreTokens()) {
                String networkToken = networkTokenizer.nextToken();

                if (networkToken.contains(TOKEN_SEPERATOR)) {
                    String[] tokenItems = networkToken.split(TOKEN_SEPERATOR);
                    if (tokenItems.length > 1) { // check if we have key and value
                        String tokenKey = tokenItems[0];
                        String tokenValue = tokenItems[1];

                        switch (tokenKey) {
                            case VIDEO_CODEC_CODE:
                                videoChannelStats.setCodec(tokenValue);
                                break;

                            case AUDIO_CODEC_CODE:
                                audioChannelStats.setCodec(tokenValue);
                                break;

                            case CONTENT_RX_CODEC_CODE:
                                contentChannelStats.setCodec(tokenValue);
                                break;

                            case PROTOCOL_CODE:
                                callStats.setProtocol(tokenValue);
                                break;

                            case PERCENT_TX_PACKETLOSS_CODE:
                                callStats.setPercentPacketLossTx(convertToFloat(tokenValue));
                                break;

                            case TOTAL_TX_PACKETLOSS_CODE:
                                callStats.setTotalPacketLossTx(convertToInteger(tokenValue));
                                break;

                            case VIDEO_RX_FRAME_SIZE_CODE:
                                if (tokenValue.contains(X_CHARACTER)) {
                                    videoChannelStats.setFrameSizeRx(tokenValue.replace(LOWER_CASE_P, ""));
                                } else {
                                    videoChannelStats.setFrameSizeRx(tokenValue);
                                }
                                break;

                            case VIDEO_TX_FRAME_SIZE_CODE:
                                if (tokenValue.contains(X_CHARACTER)) {
                                    videoChannelStats.setFrameSizeTx(tokenValue.replace(LOWER_CASE_P, ""));
                                } else {
                                    videoChannelStats.setFrameSizeTx(tokenValue);
                                }
                                break;

                            default:
                                break;
                        }
                    }
                }
            }
            if (Objects.equals(videoChannelStats.getCodec(), NULL_STATISTIC)
                    && Objects.equals(audioChannelStats.getCodec(), NULL_STATISTIC)
                    && Objects.equals(callStats.getProtocol(), NULL_STATISTIC)
                    && callStats.getPercentPacketLossTx() == null
                    && Objects.equals(videoChannelStats.getFrameSizeRx(), NULL_STATISTIC)
                    && Objects.equals(videoChannelStats.getFrameSizeTx(), NULL_STATISTIC)) {
                return singletonList(new EndpointStatistics());
            }
        } else {
            return singletonList(new EndpointStatistics());
        }

        String advancedStats = send(GET_ADVANCED_STATS);
        if (advancedStats != null && advancedStats.length() > 0) {
            // Some stats are used twice, set to local variable
            Integer audioTxRate = null;
            Integer audioRxRate = null;
            Integer videoTxRate = null;
            Integer videoRxRate = null;
            Integer contentTxRate = null;
            Integer contentRxRate = null;

            // StringTokenizer will create an "array" that is split on the Space character and End Of Line characters.
            // We can loop through the array via "hasMoreTokens". If the entry contains a ":" character, it contains data
            StringTokenizer stringTokenizer = new StringTokenizer(advancedStats);

            while (stringTokenizer.hasMoreTokens()) {
                String token = stringTokenizer.nextToken();

                if (token.contains(TOKEN_SEPERATOR)) {
                    String[] reportedStats = token.split(TOKEN_SEPERATOR);
                    if (reportedStats.length > 1) {
                        // check if we have key and value
                        String reportedKey = reportedStats[0];
                        String reportedValue = reportedStats[1];

                        switch (reportedKey) {
                            case AUDIO_TX_RATE_CODE:
                                audioTxRate = convertToInteger(reportedValue);
                                break;

                            case AUDIO_RX_RATE_CODE:
                                audioRxRate = convertToInteger(reportedValue);
                                break;

                            case VIDEO_RX_RATE_CODE:
                                videoRxRate = convertToInteger(reportedValue);
                                break;

                            case VIDEO_TX_RATE_CODE:
                                videoTxRate = convertToInteger(reportedValue);
                                break;

                            case CONTENT_RX_RATE_CODE:
                                contentRxRate = convertToInteger(reportedValue);
                                break;

                            case CONTENT_TX_RATE_CODE:
                                contentTxRate = convertToInteger(reportedValue);
                                break;

                            case AUDIO_TX_JITER_CODE:
                                audioChannelStats.setJitterTx(convertToFloat(reportedValue));
                                break;

                            case AUDIO_RX_JITTER_CODE:
                                audioChannelStats.setJitterRx(convertToFloat(reportedValue));
                                break;

                            case AUDIO_TX_PACKETLOSS_CODE:
                                audioChannelStats.setPacketLossTx(convertToInteger(reportedValue));
                                break;

                            case AUDIO_RX_PACKETLOSS_CODE:
                                audioChannelStats.setPacketLossRx(convertToInteger(reportedValue));
                                break;

                            case VIDEO_TX_JITTER_CODE:
                                videoChannelStats.setJitterTx(convertToFloat(reportedValue));
                                break;

                            case VIDEO_RX_JITTER_CODE:
                                videoChannelStats.setJitterRx(convertToFloat(reportedValue));
                                break;

                            case VIDEO_TX_PACKETLOSS_CODE:
                                videoChannelStats.setPacketLossTx(convertToInteger(reportedValue));
                                break;

                            case VIDEO_RX_PACKETLOSS_CODE:
                                videoChannelStats.setPacketLossRx(convertToInteger(reportedValue));
                                break;

                            case VIDEO_TX_BITRATE_CODE:
                                videoChannelStats.setBitRateTx(convertToInteger(reportedValue));
                                break;

                            case VIDEO_RX_BITRATE_CODE:
                                videoChannelStats.setBitRateRx(convertToInteger(reportedValue));
                                break;

                            case VIDEO_TX_FRAMERATE_CODE:
                                videoChannelStats.setFrameRateTx(convertToFloat(reportedValue));
                                break;

                            case VIDEO_RX_FRAMERATE_CODE:
                                videoChannelStats.setFrameRateRx(convertToFloat(reportedValue));
                                break;

                            case CONTENT_TX_PACKETLOSS_CODE:
                                contentChannelStats.setPacketLossTx(convertToInteger(reportedValue));
                                break;

                            case CONTENT_RX_PACKETLOSS_CODE:
                                contentChannelStats.setPacketLossRx(convertToInteger(reportedValue));
                                break;

                            case CONTENT_TX_RATE_USED_CODE:
                                contentChannelStats.setBitRateTx(convertToInteger(reportedValue));
                                break;

                            case CONTENT_RX_RATE_USED_CODE:
                                contentChannelStats.setBitRateRx(convertToInteger(reportedValue));
                                break;

                            case CONTENT_TX_FRAMERATE_CODE:
                                contentChannelStats.setFrameRateTx(convertToFloat(reportedValue));
                                break;

                            case CONTENT_RX_FRAMERATE_CODE:
                                contentChannelStats.setFrameRateRx(convertToFloat(reportedValue));
                                break;

                            default:
                                break;
                        }
                    }
                }
            }

            if (videoChannelStats.getBitRateRx() == null
                    && videoChannelStats.getBitRateTx() == null
                    && videoChannelStats.getJitterTx() == null
                    && videoChannelStats.getPacketLossRx() == null
                    && videoChannelStats.getPacketLossTx() == null
                    && videoChannelStats.getFrameRateRx() == null
                    && videoChannelStats.getFrameRateTx() == null
                    && videoChannelStats.getJitterRx() == null
                    && audioChannelStats.getBitRateTx() == null
                    && audioChannelStats.getBitRateRx() == null
                    && audioChannelStats.getJitterRx() == null
                    && audioChannelStats.getJitterTx() == null
                    && audioChannelStats.getPacketLossRx() == null
                    && audioChannelStats.getPacketLossTx() == null
                    && contentChannelStats.getBitRateRx() == null
                    && contentChannelStats.getBitRateTx() == null
                    && contentChannelStats.getPacketLossRx() == null
                    && contentChannelStats.getPacketLossTx() == null
                    && contentChannelStats.getFrameRateRx() == null
                    && contentChannelStats.getFrameRateTx() == null) {
                return singletonList(new EndpointStatistics());
            }

            // calculate transmit rate (one of the variables may be null)
            callStats.setCallRateTx(
                    Optional.ofNullable(videoTxRate).orElse(0)
                            + Optional.ofNullable(audioTxRate).orElse(0)
                            + Optional.ofNullable(contentTxRate).orElse(0));

            // calculate receive rate (one of the variables may be null)
            callStats.setCallRateRx(
                    Optional.ofNullable(videoRxRate).orElse(0)
                            + Optional.ofNullable(audioRxRate).orElse(0)
                            + Optional.ofNullable(contentRxRate).orElse(0));

            audioChannelStats.setBitRateRx(audioRxRate);
            audioChannelStats.setBitRateTx(audioTxRate);

            videoChannelStats.setBitRateRx(videoRxRate);
            videoChannelStats.setBitRateTx(videoTxRate);

            contentChannelStats.setBitRateRx(contentRxRate);
            contentChannelStats.setBitRateTx(contentTxRate);
        }
        statistics.setCallStats(callStats);
        statistics.setAudioChannelStats(audioChannelStats);
        // check video statistics if it is audio only call then we are not adding video statistics to the statistics
        // below is example of statistics in audion only call: codec bitraterx bitratetx jitterrx jittertx packetlossrx
        // packetlosstx frameraterx frameratetx framesizerx framesizetx videomutetx --- 0 0 0 0 0 0 0 0 --- ---
        if (isNotEmpty(videoChannelStats)) {
            statistics.setVideoChannelStats(videoChannelStats);
        }

        //check no content sharing
        if (isNotEmpty(contentChannelStats)) {
            cleanDisabledStats(contentChannelStats);
            statistics.setContentChannelStats(contentChannelStats);
        }

        return Arrays.asList(statistics, extendedStatistics);
    }

    private void populateCameraData(Map<String, String> statistics, List<AdvancedControllableProperty> advancedControllableProperties) throws Exception {
        String invertNear = retrieveDeviceStats(String.format(CAMERA_INVERT_NEAR, GET));
        if (validateCameraProperty(invertNear)) {
            advancedControllableProperties.add(createSwitch(CAMERA_LABEL_INVERT,
                    normalizeSwitchValueInternal(StringUtils.getDataBetween(invertNear, "get\r\ncamerainvert near ", LINE_BREAKER))));
            statistics.put(CAMERA_LABEL_INVERT, "");
        }
        String nearTracking = retrieveDeviceStats(String.format(CAMERA_NEAR_TRACKING, GET));
        if (validateCameraProperty(nearTracking)) {
            advancedControllableProperties.add(createSwitch(CAMERA_LABEL_TRACKING,
                    normalizeSwitchValueInternal(StringUtils.getDataBetween(nearTracking, "get\r\ncamera near tracking ", LINE_BREAKER))));
            statistics.put(CAMERA_LABEL_TRACKING, "");
        }
        String trackingCalibrate = retrieveDeviceStats(String.format(CAMERA_NEAR_TRACKING_CALIBRATE, GET));
        if (validateCameraProperty(trackingCalibrate)) {
            advancedControllableProperties.add(createSwitch(CAMERA_LABEL_TRACKING_CALIBRATE,
                    normalizeSwitchValueInternal(StringUtils.getDataBetween(trackingCalibrate, "get\r\ncameratracking near calibrate ", LINE_BREAKER))));
            statistics.put(CAMERA_LABEL_TRACKING_CALIBRATE, "");
        }
        String trackingFraming = retrieveDeviceStats(String.format(CAMERA_NEAR_TRACKING_FRAMING, GET));
        if (validateCameraProperty(trackingCalibrate)) {
            advancedControllableProperties.add(createDropdown(CAMERA_LABEL_TRACKING_FRAMING, Arrays.asList("wide", "medium", "tight"),
                    StringUtils.getDataBetween(trackingFraming, "get\r\ncameratracking near framing ", LINE_BREAKER)));
            statistics.put(CAMERA_LABEL_TRACKING_FRAMING, "");
        }
        String trackingMode = retrieveDeviceStats(String.format(CAMERA_NEAR_TRACKING_MODE, GET));
        if (validateCameraProperty(trackingMode)) {
            advancedControllableProperties.add(createDropdown(CAMERA_LABEL_TRACKING_MODE, Arrays.asList("off", "group", "speaker", "groupwithtransition"),
                    StringUtils.getDataBetween(trackingMode, "get\r\ncameratracking near mode ", LINE_BREAKER)));
            statistics.put(CAMERA_LABEL_TRACKING_MODE, "");
        }
        String trackingParticipant = retrieveDeviceStats(String.format(CAMERA_NEAR_TRACKING_PARTICIPANT, GET));
        if (validateCameraProperty(trackingParticipant)) {
            advancedControllableProperties.add(createSwitch(CAMERA_LABEL_TRACKING_PARTICIPANT,
                    normalizeSwitchValueInternal(StringUtils.getDataBetween(trackingParticipant, "get\r\ncameratracking near participant ", LINE_BREAKER))));
            statistics.put(CAMERA_LABEL_TRACKING_PARTICIPANT, "");
        }
        String trackingPip = retrieveDeviceStats(String.format(CAMERA_NEAR_TRACKING_PIP, GET));
        if (validateCameraProperty(trackingPip)) {
            advancedControllableProperties.add(createSwitch(CAMERA_LABEL_TRACKING_PIP,
                    normalizeSwitchValueInternal(StringUtils.getDataBetween(trackingPip, "get\r\ncameratracking near pip ", LINE_BREAKER))));
            statistics.put(CAMERA_LABEL_TRACKING_PIP, "");
        }
        String trackingWake = retrieveDeviceStats(String.format(CAMERA_NEAR_TRACKING_WAKE, GET));
        if (validateCameraProperty(trackingWake)) {
            advancedControllableProperties.add(createSwitch(CAMERA_LABEL_TRACKING_WAKE,
                    normalizeSwitchValueInternal(StringUtils.getDataBetween(trackingWake, "get\r\ncameratracking near wake ", LINE_BREAKER))));
            statistics.put(CAMERA_LABEL_TRACKING_WAKE, "");
        }
        String trackingSpeed = retrieveDeviceStats(String.format(CAMERA_NEAR_TRACKING_SPEED, GET));
        if (validateCameraProperty(trackingSpeed)) {
            advancedControllableProperties.add(createDropdown(CAMERA_LABEL_TRACKING_SPEED, Arrays.asList("off", "slow", "normal", "fast"),
                    StringUtils.getDataBetween(trackingSpeed, "get\r\ncameratracking near speed ", LINE_BREAKER)));
            statistics.put(CAMERA_LABEL_TRACKING_SPEED, "");
        }
        String videoMute = retrieveDeviceStats(String.format(VIDEOMUTE, GET));
        if (validateCameraProperty(videoMute)) {
            advancedControllableProperties.add(createSwitch(CAMERA_LABEL_MUTE,
                    normalizeSwitchValueInternal(StringUtils.getDataBetween(videoMute, "get\r\nvideomute near ", LINE_BREAKER))));
            statistics.put(CAMERA_LABEL_MUTE, "");
        }
        Map<String, Float> cameraPosition = getCameraPosition();
        if (!cameraPosition.isEmpty()) {
            advancedControllableProperties.add(createSlider(CAMERA_LABEL_PAN, -50000.0f, 50000.0f, cameraPosition.get("Pan")));
            statistics.put(CAMERA_LABEL_PAN, "");

            advancedControllableProperties.add(createSlider(CAMERA_LABEL_TILT, -50000.0f, 50000.0f, cameraPosition.get("Tilt")));
            statistics.put(CAMERA_LABEL_TILT, "");

            advancedControllableProperties.add(createSlider(CAMERA_LABEL_ZOOM, -50000.0f, 50000.0f, cameraPosition.get("Zoom")));
            statistics.put(CAMERA_LABEL_ZOOM, "");
        }
    }

    /**
     * Retrieve camera position as float[3] where
     * float[0] - Pan
     * float[1] - Tilt
     * float[2] - Zoom
     * <p>
     * Values are signed, between -50000 and 50000
     *
     * @return Map with Pan/Tilt/Zoom Float values
     * @throws Exception if any error occurs
     */
    private Map<String, Float> getCameraPosition() throws Exception {
        String cameraPosition = retrieveDeviceStats(CAMERA_NEAR_GETPOSITION);
        Map<String, Float> cameraPositionProperties = new HashMap<>();
        if (validateCameraProperty(cameraPosition)) {
            Matcher sourceMatcher = Pattern.compile("(\\S?\\d{1,5})\\s(\\S?\\d{1,5})\\s(\\S?\\d{1,5})").matcher(cameraPosition);
            if (sourceMatcher.find()) {
                cameraPositionProperties.put("Pan", Float.parseFloat(sourceMatcher.group(1)));
                cameraPositionProperties.put("Tilt", Float.parseFloat(sourceMatcher.group(2)));
                cameraPositionProperties.put("Zoom", Float.parseFloat(sourceMatcher.group(3)));
            }
        }
        return cameraPositionProperties;
    }

    /**
     * Validate if camera property exists and is supported, otherwise - return false
     *
     * @param value - return value for a specific command
     * @return boolean indicating whether property is valid and values can be extracted, or not
     */
    private boolean validateCameraProperty(String value) {
        if (StringUtils.isNullOrEmpty(value, true)) {
            return false;
        }
        if (value.contains("only supported")) {
            /**
             * If the property is not supported - the message would be similar to
             * "this feature is only supported for eagle eye director 2", indicating the device needs for this
             * functionality to be supported.
             */
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot add controllable property: " + value);
            }
            return false;
        }
        return true;
    }

    /**
     * Get microphones status and volume level of the device, to build controllable properties for these parameters.
     *
     * @param statistics                     ExtendedStatistics map, that contains all the statistics properties
     * @param advancedControllableProperties list of controllable properties, to add current properties to
     * @throws Exception if any error occurs
     */
    private void populateAudioData(Map<String, String> statistics, List<AdvancedControllableProperty> advancedControllableProperties) throws Exception {
        advancedControllableProperties.add(createSwitch(AUDIO_LABEL_MUTE, Objects.equals(retrieveMuteStatus(), MuteStatus.Muted) ? 1 : 0));
        statistics.put(AUDIO_LABEL_MUTE, "");

        String volume = send(String.format(VOLUME, GET));

        if (StringUtils.isNullOrEmpty(volume, true)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Empty volume command response, skipping.");
            }
            return;
        }
        String volumeLevel = StringUtils.getDataBetween(volume, "get\r\nvolume ", LINE_BREAKER);

        if (StringUtils.isNullOrEmpty(volumeLevel, true)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Empty volume level command response, skipping.");
            }
            return;
        }
        advancedControllableProperties.add(createSlider(AUDIO_LABEL_VOLUME, 0.0f, 50.0f, Float.valueOf(volumeLevel)));
        statistics.put(AUDIO_LABEL_VOLUME, "");
    }

    /**
     * Get basic device information, based on the {@link #WHOAMI} command result
     *
     * @param statistics ExtendedStatistics map, that contains all the statistics properties
     * @throws Exception if any error occurs
     */
    private void populateDeviceData(Map<String, String> statistics) throws Exception {
        String whoamiLines = retrieveDeviceStats(WHOAMI);
        if (StringUtils.isNullOrEmpty(whoamiLines, true)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Empty whoami command response, skipping.");
            }
            return;
        }
        addStatisticsProperty(statistics, "Device#Name", StringUtils.getDataBetween(whoamiLines, "Hi, my name is : ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#Model", StringUtils.getDataBetween(whoamiLines, "Model: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#SoftwareVersion", StringUtils.getDataBetween(whoamiLines, "Software Version: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#SerialNumber", StringUtils.getDataBetween(whoamiLines, "Serial Number: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#Build", StringUtils.getDataBetween(whoamiLines, "Build Information: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#TimeInLastCall", StringUtils.getDataBetween(whoamiLines, "Time In Last Call: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#TimeInCallsTotal", StringUtils.getDataBetween(whoamiLines, "Total Time In Calls: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#TotalCalls", StringUtils.getDataBetween(whoamiLines, "Total Calls: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#SNTPTimeService", StringUtils.getDataBetween(whoamiLines, "SNTP Time Service: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#LocalTime", StringUtils.getDataBetween(whoamiLines, "Local Time is: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#H323Enabled", StringUtils.getDataBetween(whoamiLines, "H323 Enabled: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#HTTPEnabled", StringUtils.getDataBetween(whoamiLines, "HTTP Enabled: ", LINE_BREAKER));
        addStatisticsProperty(statistics, "Device#SNMPEnabled", StringUtils.getDataBetween(whoamiLines, "SNMP Enabled: ", LINE_BREAKER));
    }

    /**
     * Get basic device information, based on the {@link #STATUS} command result
     *
     * @param statistics ExtendedStatistics map, that contains all the statistics properties
     * @throws Exception if any error occurs
     */
    private void extractDeviceStatus(Map<String, String> statistics, String status) {
        if (StringUtils.isNullOrEmpty(status, true)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Empty status command response, skipping.");
            }
            return;
        }
        addStatisticsProperty(statistics, "SystemStatus#IPNetwork", StringUtils.getDataBetween(status, "ipnetwork ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#TrackableCamera", StringUtils.getDataBetween(status, "trackablecamera ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#AutoAnswerP2P", StringUtils.getDataBetween(status, "autoanswerp2p ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#SIPServer", StringUtils.getDataBetween(status, "sipserver ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#Camera", StringUtils.getDataBetween(status, "camera ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#Provisioning", StringUtils.getDataBetween(status, "provisioning ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#Audio", StringUtils.getDataBetween(status, "audio ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#RemoteControl", StringUtils.getDataBetween(status, "remotecontrol ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#LogThreshold", StringUtils.getDataBetween(status, "logthreshold ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#GlobalDirectory", StringUtils.getDataBetween(status, "globaldirectory ", LINE_BREAKER));
        addStatisticsProperty(statistics, "SystemStatus#Calendar", StringUtils.getDataBetween(status, "calendar ", LINE_BREAKER));
    }

    /**
     * Add statistics property for ExtendedStatistics map, if property value is not null or empty
     *
     * @param statistics    ExtendedStatistics map, that contains all the statistics properties
     * @param propertyName  name of the property to add
     * @param propertyValue value of the property, to make a check upon and add to the statistics map
     */
    private void addStatisticsProperty(Map<String, String> statistics, String propertyName, String propertyValue) {
        if (!StringUtils.isNullOrEmpty(propertyValue, true)) {
            statistics.put(propertyName, propertyValue.trim());
        }
    }

    /***
     * Create AdvancedControllableProperty slider instance
     *
     * @param name name of the control
     * @param initialValue initial value of the control
     * @param rangeStart start value for the slider
     * @param rangeEnd end value for the slider
     *
     * @return AdvancedControllableProperty slider instance
     */
    private AdvancedControllableProperty createSlider(String name, Float rangeStart, Float rangeEnd, Float initialValue) {
        AdvancedControllableProperty.Slider slider = new AdvancedControllableProperty.Slider();
        slider.setLabelStart(String.valueOf(rangeStart));
        slider.setLabelEnd(String.valueOf(rangeEnd));
        slider.setRangeStart(rangeStart);
        slider.setRangeEnd(rangeEnd);

        return new AdvancedControllableProperty(name, new Date(), slider, initialValue);
    }

    /***
     * Create AdvancedControllableProperty preset instance
     * @param name name of the control
     * @param initialValue initial value of the control
     * @return AdvancedControllableProperty preset instance
     */
    private AdvancedControllableProperty createDropdown(String name, List<String> values, String initialValue) {
        AdvancedControllableProperty.DropDown dropDown = new AdvancedControllableProperty.DropDown();
        dropDown.setOptions(values.toArray(new String[0]));
        dropDown.setLabels(values.toArray(new String[0]));

        return new AdvancedControllableProperty(name, new Date(), dropDown, initialValue);
    }

    /**
     * Create a switch controllable property
     *
     * @param name   name of the switch
     * @param status initial switch state (0|1)
     * @return AdvancedControllableProperty button instance
     */
    private AdvancedControllableProperty createSwitch(String name, int status) {
        AdvancedControllableProperty.Switch toggle = new AdvancedControllableProperty.Switch();
        toggle.setLabelOff("Off");
        toggle.setLabelOn("On");

        AdvancedControllableProperty advancedControllableProperty = new AdvancedControllableProperty();
        advancedControllableProperty.setName(name);
        advancedControllableProperty.setValue(status);
        advancedControllableProperty.setType(toggle);
        advancedControllableProperty.setTimestamp(new Date());

        return advancedControllableProperty;
    }

    /**
     * {@inheritDoc} <br>
     * <b>PolycomGroupSeries Specific Implementation notes:</b><br>
     * After sending dial command we poll for the status of the new call. If we cannot verify (via matching of the dialString (of the DialDevice) to the
     * remoteAddress (of the call stats returned from the device) null is returned, however if the two are matched then the call id is returned.
     */
    @Override
    public String dial(DialDevice device) throws Exception {
        // dial manual "speed" "dialstr1" [dialstr] [h323|ip|sip]
        // hangup video [callid]
        String command = null;
        Integer callSpeed = device.getCallSpeed();
        String speed = "1920";// speed has to have some value in order for dial string to be valid
        if (null != callSpeed && callSpeed.intValue() > 0) {
            speed = callSpeed.toString();
        }
        // protocol is not exactly device.getProtocol().toString() for dial string;
        command = "dial manual " + speed + " " + device.getDialString();
        Protocol protocol = device.getProtocol();
        if (nonNull(protocol)) {
            command += " " + protocol.name().toLowerCase();
        }
        send(command);
		/*		Dials a video call number dialstr1 at speed of type
				h323. Requires the parameters "speed" and "dialstr".
				Allows the user to automatically dial a number. .
				Deprecated. Instead of this command, Polycom
				recommends using dial manual and not specifying
				a call type.
		*/

        // TODO add comments that we need to go gather the call id rather then getting it from dial results (update others like this as well). also the fact
        // that we are checking it against remote address. put counter here with max number of attempts, but do a loop, we less interval then 3 seconds, have it
        // configurable(extracted properties)

        for (int i = 0; i < MAX_STATUS_POLL_ATTEMPT; i++) {
            CallStats callStats = parseCallIdAndRemoteAddress(retrieveRawCallStatistics());
            if (null != callStats) {
                String remoteAddress = callStats.getRemoteAddress();
                if (!StringUtils.isNullOrEmpty(remoteAddress, true) && remoteAddress.trim().equals(device.getDialString().trim())) {
                    return callStats.getCallId();
                }
            }
            Thread.sleep(RETRY_INTERVAL_MILLISEC);
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void hangup(String callId) throws Exception {
        // hangup all
        // hangup video [callid]
        String command = null;
        if (StringUtils.isNullOrEmpty(callId, true)) {
            command = HANGUP_ALL;
        } else {
            command = "hangup video " + callId;
        }
        send(command);
    }

    @Override
    public CallStatus retrieveCallStatus(String callId) throws Exception {
        CallStatus callStatus = new CallStatus();
        CallStats callStats = parseCallIdAndRemoteAddress(retrieveRawCallStatistics());
        if (null != callStats) {
            String currentCallId = callStats.getCallId();
            if (StringUtils.isNullOrEmpty(callId, true) || !StringUtils.isNullOrEmpty(currentCallId, true) && currentCallId.equals(callId)) {
                callStatus.setCallId(currentCallId);
                callStatus.setCallStatusState(CallStatusState.Connected);
            } else {
                callStatus.setCallId(callId);
                callStatus.setCallStatusState(CallStatusState.Disconnected);
            }
            return callStatus;
        }
        callStatus.setCallId(callId);
        callStatus.setCallStatusState(CallStatusState.Disconnected);
        return callStatus;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MuteStatus retrieveMuteStatus() throws Exception {
        String responseMuteStatus = send(GET_MUTE_STATUS);
        if (responseMuteStatus.contains(MUTE_STATUS) || responseMuteStatus.contains(MUTE_NEAR_ON)) {
            return MuteStatus.Muted;
        } else if (responseMuteStatus.contains(MUTE_NEAR_OFF)) {
            return MuteStatus.Unmuted;
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMessage(PopupMessage message) throws Exception {
        throw new NotImplementedException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mute() throws Exception {
        send(MUTE_NEAR_ON);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unmute() throws Exception {
        send(MUTE_NEAR_OFF);
    }


    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {
        String property = controllableProperty.getProperty();
        String value = String.valueOf(controllableProperty.getValue());

        switch (property) {
            case AUDIO_LABEL_VOLUME:
                send(String.format(VOLUME, SET) + removeDecimalPoint(value));
                break;
            case AUDIO_LABEL_MUTE:
                if ("0".equals(value)) {
                    unmute();
                } else {
                    mute();
                }
                break;
            case CAMERA_LABEL_PAN:
                Map<String, Float> cameraPosition = getCameraPosition();
                send(String.format(CAMERA_NEAR_SETPOSITION, removeDecimalPoint(value),
                        removeDecimalPoint(String.valueOf(cameraPosition.get("Tilt"))), removeDecimalPoint(String.valueOf(cameraPosition.get("Zoom")))));
                break;
            case CAMERA_LABEL_TILT:
                cameraPosition = getCameraPosition();
                send(String.format(CAMERA_NEAR_SETPOSITION, removeDecimalPoint(String.valueOf(cameraPosition.get("Pan"))),
                        removeDecimalPoint(value), removeDecimalPoint(String.valueOf(cameraPosition.get("Zoom")))));
                break;
            case CAMERA_LABEL_ZOOM:
                cameraPosition = getCameraPosition();
                send(String.format(CAMERA_NEAR_SETPOSITION, removeDecimalPoint(String.valueOf(cameraPosition.get("Pan"))),
                        removeDecimalPoint(String.valueOf(cameraPosition.get("Tilt"))), removeDecimalPoint(value)));
                break;
            case CAMERA_LABEL_MUTE:
                send(String.format(VIDEOMUTE, normalizeSwitchValueExternal(value)));
                break;
            case CAMERA_LABEL_INVERT:
                send(String.format(CAMERA_INVERT_NEAR, normalizeSwitchValueExternal(value)));
                break;
            case CAMERA_LABEL_TRACKING:
                send(String.format(CAMERA_NEAR_TRACKING, normalizeSwitchValueExternal(value)));
                break;
            case CAMERA_LABEL_TRACKING_CALIBRATE:
                send(String.format(CAMERA_NEAR_TRACKING_CALIBRATE, normalizeSwitchValueExternal(value)));
                break;
            case CAMERA_LABEL_TRACKING_FRAMING:
                send(String.format(CAMERA_NEAR_TRACKING_FRAMING, value));
                break;
            case CAMERA_LABEL_TRACKING_MODE:
                send(String.format(CAMERA_NEAR_TRACKING_MODE, value));
                break;
            case CAMERA_LABEL_TRACKING_PARTICIPANT:
                send(String.format(CAMERA_NEAR_TRACKING_PARTICIPANT, normalizeSwitchValueExternal(value)));
                break;
            case CAMERA_LABEL_TRACKING_PIP:
                send(String.format(CAMERA_NEAR_TRACKING_PIP, value));
                break;
            case CAMERA_LABEL_TRACKING_WAKE:
                send(String.format(CAMERA_NEAR_TRACKING_WAKE, normalizeSwitchValueExternal(value)));
                break;
            case CAMERA_LABEL_TRACKING_SPEED:
                send(String.format(CAMERA_NEAR_TRACKING_SPEED, value));
                break;
            default:
                logger.trace("Command operation is not supported: " + property);
                break;
        }
    }

    /**
     * Normalize switch values for using in SSH commands.
     * All "0"|"1" values should be changed to "off"|"on" accordingly for the device to understand the command
     *
     * @param value value, generated by Symphony switch controllable property, 0|1
     * @return String value, "on"|"off"
     */
    private String normalizeSwitchValueExternal(String value) {
        return "0".equals(value) ? "off" : "on";
    }

    /**
     * Normalize switch values for using in Symphony controllable properties
     * All  "off"|"on" values should be changed to "0"|"1" accordingly for Symphony to understand it and set a proper
     * current value for the switch
     *
     * @param value value, reported by device, "on"|"off"
     * @return int value, 0|1
     */
    private int normalizeSwitchValueInternal(String value) {
        return "off".equals(value) ? 0 : 1;
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        if (CollectionUtils.isEmpty(list)) {
            throw new IllegalArgumentException("Controllable properties cannot be null or empty");
        }

        for (ControllableProperty controllableProperty : list) {
            controlProperty(controllableProperty);
        }
    }

    /**
     * Removing decimal point for slider command actions.
     * By default, Symphony handles Slider controls using values with a decimal point values.
     * Cisco API does not support such values, so for any slider control operation, values should not have a decimal
     * point
     *
     * @param value of the control operation
     * @return {@link String} value without a decimal point
     * @throws RuntimeException if original value is null or empty
     */
    private static String removeDecimalPoint(String value) {
        if (StringUtils.isNullOrEmpty(value, true)) {
            throw new RuntimeException("Unable to create a control operation with null or empty control value.");
        } else {
            return String.format("%.0f", Float.valueOf(value));
        }
    }

    /**
     * This method only populates call id and remote address for generic use from any methods that just need these 2 call stats.
     *
     * @param rawCallStatistics the data to parse the stats from, if null we query the device for this
     * @return CallStats with only callId and remoteAddress populated
     */
    private static CallStats parseCallIdAndRemoteAddress(String[] rawCallStatistics) throws Exception {
        CallStats callStats = null;
        if (null != rawCallStatistics && 1 < rawCallStatistics.length) {
            callStats = new CallStats();
            callStats.setCallId(rawCallStatistics[1]);
            if (rawCallStatistics.length > 2) {
                callStats.setRemoteAddress(rawCallStatistics[3]);
            }
        }
        return callStats;
    }

    private String[] retrieveRawCallStatistics() throws Exception {
        String activeCallStatus = send(GET_CALL_STATE);
        if (!StringUtils.isNullOrEmpty(activeCallStatus, true)) {
            String[] callInfoArray = activeCallStatus.split(TOKEN_SEPERATOR);
            if (callInfoArray.length < 6 || callInfoArray[5] == null || !callInfoArray[5].equals(CONNECTED)) {
                // not in a call
                return null;
            }
            return callInfoArray;
        }
        return null;
    }

    /**
     * Retrieves H323 and SIP registration stats. Returns null if registration status cannot be obtained or not applicable
     *
     * @return {@link RegistrationStatus}
     * @throws Exception if any error occurs
     */
    private RegistrationStatus extractRegistrationStatus(String status) throws Exception {
        RegistrationStatus registrationStatus = new RegistrationStatus();
        registrationStatus.setH323Registered(false);
        registrationStatus.setSipRegistered(false);

        // use replace all and regex to remove all alphabetic characters (leaving only the ip address of the registrar)
        String sipRegistrarIpString = send(SYSTEMSETTING_GET_SIPREGISTRARSERVER).replaceAll(REGEX_REMOVE_ALL_ALPHABETIC_CHARACTERS, "");
        if (!StringUtils.isNullOrEmpty(sipRegistrarIpString, true)) {
            registrationStatus.setSipRegistrar(sipRegistrarIpString);
        }

        // use replace all and regex to remove all alphabetic characters (leaving only the ip address of the gatekeeper)
        String gatekeeperIpString = send(GATEKEEPERIP_GET).replaceAll(REGEX_REMOVE_ALL_ALPHABETIC_CHARACTERS, "");
        if (!StringUtils.isNullOrEmpty(gatekeeperIpString, true)) {
            registrationStatus.setH323Gatekeeper(gatekeeperIpString);
        }

        String gateKeeper = StringUtils.getDataBetween(status, "gatekeeper ", LINE_BREAKER);
        if (gateKeeper != null) {
            switch (gateKeeper) {
                case "online": {
                    registrationStatus.setH323Registered(true);
                    break;
                }
                case "offline": {
                    registrationStatus.setH323Registered(false);
                    break;
                }
                default:
                    if (logger.isDebugEnabled()) {
                        logger.debug("H323 gatekeeper status is not available: " + gateKeeper);
                    }
                    break;
            }
        }
        String registrar = StringUtils.getDataBetween(status, "sipserver ", LINE_BREAKER);
        if (registrar != null) {
            switch (registrar) {
                case "online": {
                    registrationStatus.setSipRegistered(true);
                    break;
                }
                case "offline": {
                    registrationStatus.setSipRegistered(false);
                    break;
                }
                default:
                    if (logger.isDebugEnabled()) {
                        logger.debug("SIP registrar status is not available: " + registrar);
                    }
                    break;
            }
        }

        return registrationStatus;
    }

    /**
     * Get the result of {@link #STATUS} command, if supported by the device
     *
     * @return {@link String} result of the command, or empty value if the command is not supported
     */
    private String retrieveStatus() {
        String status = "";
        try {
            status = send(STATUS);
        } catch (Exception e) {
            if (logger.isTraceEnabled()) {
                logger.trace("This device does not support or recognize the Status command (Polycom needs to be Verion 6 or above)");
            }
        }
        return status;
    }

    /**
     * Get the result of device command, if supported by the device
     *
     * @return {@link String} result of the command, or empty value if the command is not supported
     */
    private String retrieveDeviceStats(String command) {
        String response = "";
        try {
            response = send(command);
        } catch (Exception e) {
            if (logger.isTraceEnabled()) {
                logger.trace(String.format("This device does not support or recognize the %s command", command));
            }
        }
        return response;
    }
}

/*
 The following commands are sent to the Polycom Group Series.  Each command has a sample of the expected return data.
  
 GET_CALL_STATE = "callinfo all"; 
 The callid information is returned using the following format:
 callinfo:<callid>:<far site name>:<far site number>:<speed>:
 <connection status>:<mute status>:<call direction>:<call type>
  
 Data returned when not in a call: system in not in a call
 Data returned from "callinfo all" when in a call is shown below.  The string is split on the ":" character to extract data.

callinfo begin
callinfo:3:KOP.Lab.HDX9000:67.110.19.31:64:connected:muted:outgoing:videocall
callinfo end

 GET_ADVANCED_STATS = "advnetstats"
 Data returned from "advnetstats" when in a call is shown below.  This string is first parsed with StringTokenizer, and then 
 parsed with the ":" character to extract data.

call:0 tar:16 K rar:16 K tvr:48 K rvr:48 K
tvru:42 K rvru:43 K tvfr:29 rvfr:29 vfe:0
tapl:0 rapl:0 taj:3 ms raj:3 ms tvpl:0 rvpl:0
tvj:4 ms rvj:9 ms dc:Disabled rsid:7771040001@vnoc1.com ccaps:---


from api guide* abbreviations are: 
tar Transmit audio rate
rar Receive audio rate
tvr Transmit video rate
rvr Receive video rate
tvru Transmit video rate used
rvru Receive video rate used
tvfr Transmit video frame rate
rvfr Receive video frame rate
vfe Video FEC errors
tapl Transmit audio packet loss (H.323 calls only) 
tlsdp Transmit LSD protocol (H.320 calls only)
rapl Receive audio packet loss (H.323 calls only)
rlsdp Receive LSD protocol (H.320 calls only)
taj Transmit audio jitter (h.323 calls only)
tlsdr Transmit LSD rate (H.320 calls only)
raj Receive audio jitter (H.323 calls only)
rlsdp Receive LSD rate (H.320 calls only)
tvpl Transmit video packet loss (H.323 calls only)
tmlpp Transmit MLP protocol (H.320 calls only)
rvpl Receive video packet loss (H.323 calls only)
rmlpp Receive MLP protocol (H.320 calls only)
tvj Transmit video jitter (H.323 calls only)
tmlpr Transmit MLP rate (H.320 calls only)
rvj Receive video jitter (H.323 calls only)
rmlpr Receive MLP rate (H.320 calls only)
dc Data conference
rsid Remote system id
* support.polycom.com/global/documents/support/user/products/video/api_guide_6.0.pdf


 GET_NETWORK_STATS = "netstats";
 Data returned from "netstats" when in a call is shown below.  This string is first parsed with StringTokenizer, and then 
 parsed with the ":" character to extract data.

call:0 txrate:64 K rxrate:64 K pktloss:0 %pktloss:0.0 %
tvp:H.264 rvp:H.264 tvf:640x368 rvf:SIF tap:G.722.1 rap:G.722.1 tcp:H.323 rcp:H.323



-> status
inacall offline
autoanswerp2p online
remotecontrol online
microphones online
visualboard online
globaldirectory offline
ipnetwork online
gatekeeper online
sipserver online
logthreshold offline
meetingpassword offline
rpms offline
status end


*/