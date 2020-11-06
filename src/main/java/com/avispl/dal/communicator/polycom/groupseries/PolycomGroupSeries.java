/*
 * Copyright (c) 2015-2020 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.dal.communicator.polycom.groupseries;

import com.avispl.dal.communicator.polycom.groupseries.utils.StringUtils;
import com.avispl.symphony.api.dal.control.call.CallController;
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

import javax.security.auth.login.FailedLoginException;
import java.util.*;

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

public class PolycomGroupSeries extends SshCommunicator implements CallController, Monitorable {
    /*
     *	See https://support.polycom.com/content/dam/polycom-support/products/telepresence-and-video/g7500/user/en/g7500-command-line-api-reference-guide.pdf
     *	for tokens details
     */
    private static final String REGEX_REMOVE_ALL_ALPHABETIC_CHARACTERS = "[^\\d.]";
    private static final String HANGUP_ALL = "hangup all";
    private static final String LOWER_CASE_P = "p";
    private static final String GATEKEEPERIP_GET = "gatekeeperip get";
    private static final String SYSTEMSETTING_GET_SIPREGISTRARSERVER = "systemsetting get sipregistrarserver";
    private static final String STATUS = "status";
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
    private static final String X_CHARACTER = "x";
    private static final String NULL_STATISTIC = "---";
    private static final int MAX_STATUS_POLL_ATTEMPT = 20; // TODO extract into configurable property
    private static final int RETRY_INTERVAL_MILLISEC = 1000; // TODO extract into configurable property

    private static void cleanDisabledStats(ContentChannelStats stats) {
        if (stats.getFrameRateRx() == 0.0 &&
                (stats.getFrameSizeRx() == null || Objects.equals(stats.getFrameSizeRx(), NULL_STATISTIC))
                && stats.getBitRateRx() == 0) {
            stats.setFrameRateRx(null);
            stats.setBitRateRx(null);
            stats.setPacketLossRx(null);
        }
        if (stats.getFrameRateTx() == 0.0 && (stats.getFrameSizeTx() == null
                || Objects.equals(stats.getFrameSizeTx(), NULL_STATISTIC)) && stats.getBitRateTx() == 0) {
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
        CallStats callStats = null;

        // Add code to return registration status
        RegistrationStatus registrationStats = retrieveRegistrationStatus();
        statistics.setRegistrationStatus(registrationStats);

        String[] activeCallStatus = retrieveRawCallStatistics();
        if (null == activeCallStatus) {
            statistics.setInCall(false);
            return singletonList(statistics);
        }

        statistics.setInCall(true);
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

        return singletonList(statistics);
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
				if(nonNull(protocol)) {
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
				if (!StringUtils.isNullOrEmpty(remoteAddress) && remoteAddress.trim().equals(device.getDialString().trim())) {
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
        if (StringUtils.isNullOrEmpty(callId)) {
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
            if (StringUtils.isNullOrEmpty(callId) || !StringUtils.isNullOrEmpty(currentCallId) && currentCallId.equals(callId)) {
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
        // The command showpopup did not work with polycom GS
        // String command = "showpopup \"" + message.getMessage() + "\"";
        // send(command);
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
        if (!StringUtils.isNullOrEmpty(activeCallStatus)) {
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
    private RegistrationStatus retrieveRegistrationStatus() throws Exception {
        RegistrationStatus registrationStatus = new RegistrationStatus();

        // use replace all and regex to remove all alphabetic characters (leaving only the ip address of the registrar)
        String sipRegistrarIpString = send(SYSTEMSETTING_GET_SIPREGISTRARSERVER).replaceAll(REGEX_REMOVE_ALL_ALPHABETIC_CHARACTERS, "");
        if (!StringUtils.isNullOrEmpty(sipRegistrarIpString)) {
            registrationStatus.setSipRegistrar(sipRegistrarIpString);
        }

        // use replace all and regex to remove all alphabetic characters (leaving only the ip address of the gatekeeper)
        String gatekeeperIpString = send(GATEKEEPERIP_GET).replaceAll(REGEX_REMOVE_ALL_ALPHABETIC_CHARACTERS, "");
        if (!StringUtils.isNullOrEmpty(gatekeeperIpString)) {
            registrationStatus.setH323Gatekeeper(gatekeeperIpString);
        }
        try {
            String status = send(STATUS);
            String gateKeeper = StringUtils.getDataBetween(status, "gatekeeper ", "\r\r\n");
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
                }
            }
            String registrar = StringUtils.getDataBetween(status, "sipserver ", "\r\r\n");
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
                }
            }
        } catch (Exception e) {
            if (logger.isTraceEnabled()) {
                logger.trace("This device does not support or recognize the Status command (Polycom needs to be Verion 6 or above)");
            }
        }

        return registrationStatus;
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