/*
 * Copyright (c) 2015-2017 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.dal.communicator.polycom.groupseries.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Contains string related static helpers.
 * 
 * @author Shervin Mostashfi / Symphony Dev Team<br>
 *         Created on Feb 13, 2015
 * @since 2.4
 */
public final class StringUtils {
	// TODO need also to report certain errors to the cloud
	private static final Log LOG = LogFactory.getLog(StringUtils.class);


	/**
	 * Checks to see if {@code string} is not null or empty
	 *
	 * @param string to be evaluated
	 * @return {@code true} if string is not null or empty<br>
	 *         {@code false} if otherwise
	 */
	public static boolean isNotNullOrEmpty(String string) {
		return !isNullOrEmpty(string, false);
	}

	/**
	 * Checks to see if {@code string} is null or empty
	 * 
	 * @param string to be evaluated
	 * @return {@code true} if string is null or empty<br>
	 *         {@code false} if otherwise
	 */
	public static boolean isNullOrEmpty(String string) {
		return isNullOrEmpty(string, false);
	}

	/**
	 * Checks to see if {@code string} is {@code null} or empty. <br>
	 * If {@code trim} is {@code true}, string is trimmed before checking if empty.
	 * 
	 * @param string string to check
	 * @param trim whether to trim string before checking if empty
	 * @return {@code true} if provided string is {@code null} of empty, {@code false} otherwise.
	 */
	public static boolean isNullOrEmpty(String string, boolean trim) {
		return string == null || (trim ? string.trim().isEmpty() : string.isEmpty());
	}

	/**
	 * Local procedure to take a string and return an Float. If parsing fails it will remove all non-numeric characters and try parsing again.
	 * 
	 * @param floatString the string value to be converted
	 * @return converted Float, or {@code null} if provided string cannot be converted
	 */
	public static Float convertToFloat(String floatString) {
		if (floatString == null) {
			return null;
		}
		Float returnVal = null;
		try {
			returnVal = Float.parseFloat(floatString);
		} catch (Exception ex1) {
			floatString = floatString.replaceAll("[^0-9.]", "");
			try {
				returnVal = Float.parseFloat(floatString);
			} catch (Exception ex2) {
				LOG.error("Could not convert string \"" + floatString + "\" to Float", ex2);
			}
		}
		return returnVal;
	}

	/**
	 * Local procedure to take a string and return an Integer. The method will first try to parse as an integer if that fails it will try to parse as a float
	 * then round the results. if its still failing we remove all non-numeric characters and try the two parsing methods again.
	 * 
	 * @param integerString the string value to be converted
	 * @return converted Integer, or {@code null} if provided string cannot be converted
	 */
	public static Integer convertToInteger(String integerString) {
		if (integerString == null) {
			return null;
		}
		Integer returnVal = null;
		try {
			returnVal = Integer.parseInt(integerString);
		} catch (Exception ex1) {
			try {
				returnVal = Math.round(Float.parseFloat(integerString));
			} catch (Exception ex2) {
				integerString = integerString.replaceAll("[^0-9.]", "");
				try {
					returnVal = Integer.parseInt(integerString);
				} catch (Exception ex3) {
					try {
						returnVal = Math.round(Float.parseFloat(integerString));
					} catch (Exception ex4) {
						LOG.error("Could not convert string \"" + integerString + "\" to Integer", ex4);
					}
				}
			}
		}
		return returnVal;
	}

	/**
	 * Local procedure to take a string and return a Double. The method will first try to parse as an Double if that fails it will try to parse as a float then
	 * cast the results. if its still failing we remove all non-numeric characters and try the two parsing methods again.
	 * 
	 * @param doubleString the string value to be converted
	 * @return converted Double, or {@code null} if provided string cannot be converted
	 */
	public static Double convertToDouble(String doubleString) {
		if (doubleString == null) {
			return null;
		}

		Double returnVal = null;
		try {
			returnVal = Double.parseDouble(doubleString);
		} catch (Exception ex1) {
			try {
				returnVal = (double) Math.round(Float.parseFloat(doubleString));
			} catch (Exception ex2) {
				doubleString = doubleString.replaceAll("[^0-9.]", "");
				try {
					returnVal = Double.parseDouble(doubleString);
				} catch (Exception ex3) {
					try {
						returnVal = (double) Math.round(Float.parseFloat(doubleString));
					} catch (Exception ex4) {
						LOG.error("Could not convert string \"" + doubleString + "\" to Double", ex4);
					}
				}
			}
		}
		return returnVal;
	}

	/**
	 * Finds and returns the string between two specified strings
	 * 
	 * @param source the initial string that contains the data
	 * @param a start string
	 * @param b end string
	 * @return the string we are looking for or null
	 */
	public static String getDataBetween(String source, String a, String b) {
		int posA = source.indexOf(a);
		if (posA < 0) {
			return null;
		}
		int adjustedPosA = posA + a.length();
		int posB = source.indexOf(b, adjustedPosA);
		if (posB < 0) {
			return null;
		}
		return source.substring(adjustedPosA, posB);
	}

}
