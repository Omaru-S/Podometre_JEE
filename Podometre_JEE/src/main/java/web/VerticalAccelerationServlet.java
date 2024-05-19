package web;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Queue;
import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet("/verticalAcceleration")
public class VerticalAccelerationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private float verticalAcceleration;
    private float time = 0f;
    private Queue<Float> accelerationBuffer = new LinkedList<>();

    // Constants for sample rate and FFT size
    private static final int SAMPLE_RATE = 100;  // Sample rate in Hz
    private static final int FFT_SIZE = 1024;     // Size of the FFT, choose as a power of two
    private static final float TIME = FFT_SIZE/SAMPLE_RATE;
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Read JSON data from request
        StringBuilder jsonBuffer = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                jsonBuffer.append(line);
            }
        }

        // Parse JSON data
        String jsonString = jsonBuffer.toString();
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray verticalAccelerations = jsonObject.getJSONArray("verticalAccelerations");
        time = jsonObject.getFloat("time");

        // Log received data and update buffer for each acceleration
        //System.out.println("Received time: " + time + " and verticalAccelerations: " + verticalAccelerations);
        for (int i = 0; i < verticalAccelerations.length(); i++) {
            float acceleration = verticalAccelerations.getFloat(i);
            updateAccelerationBuffer(acceleration);
        }

        // Generate response JSON
        JSONObject responseJson = new JSONObject();
        responseJson.put("steps", calculateSteps(time));  // Calculate steps using the full buffer and time
        responseJson.put("buffer", new JSONArray(accelerationBuffer));

        // Send response
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.print(responseJson.toString());
        out.flush();
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Serve the latest vertical acceleration
		/*
		 * String accelerationParam = request.getParameter("verticalAcceleration"); if
		 * (accelerationParam != null) { verticalAcceleration =
		 * Float.parseFloat(accelerationParam);
		 * 
		 * // Update acceleration buffer updateAccelerationBuffer(verticalAcceleration);
		 * }
		 * 
		 * // Serve time since the last request String timeParam =
		 * request.getParameter("time"); if (timeParam != null) { time =
		 * Float.parseFloat(timeParam); }
		 * 
		 * // Log parameters received in GET request
		 * System.out.println("GET request received with verticalAcceleration: " +
		 * verticalAcceleration + ", time: " + time);
		 */

        // Generate response JSON
        JSONObject responseJson = new JSONObject();
        responseJson.put("time", time);
        responseJson.put("steps", calculateSteps(time));
        responseJson.put("buffer", new JSONArray(accelerationBuffer));

        // Send response
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.print(responseJson.toString());
        out.flush();
    }

    private void updateAccelerationBuffer(float newAcceleration) {
        if (accelerationBuffer.size() >= FFT_SIZE) {
            accelerationBuffer.poll();
        }
        accelerationBuffer.add(newAcceleration);
        //System.out.println("Updated accelerationBuffer: " + accelerationBuffer);
    }

    private int calculateSteps(float time) {
        if (accelerationBuffer.size() != FFT_SIZE) {
            return 0;
        }
        float[] accelArray = convertQueueToArray(accelerationBuffer);
        //System.out.println("Acceleration array for FFT: " + java.util.Arrays.toString(accelArray));

        double[] fftResult = performFFT(accelArray);
        //System.out.println("FFT result magnitudes: " + java.util.Arrays.toString(fftResult));

        float fundamentalFrequency = findFundamentalFrequency(fftResult);
        System.out.println("Calculated fundamental frequency: " + fundamentalFrequency);
        System.out.println("Time : " + time);
        float cadence = fundamentalFrequency;  // steps per second
        int steps = (int) (cadence * TIME);
        System.out.println("Calculated steps: " + steps);
        System.out.println("---------------------");
        
        return steps;
    }

    private float[] convertQueueToArray(Queue<Float> queue) {
        float[] array = new float[queue.size()];
        int index = 0;
        for (Float f : queue) {
            array[index++] = (f != null ? f : 0.0f);
        }
        return array;
    }

    private double[] performFFT(float[] input) {
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        double[] inputDouble = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            inputDouble[i] = input[i];
        }
        Complex[] complexResult = fft.transform(inputDouble, TransformType.FORWARD);
        double[] magnitudes = new double[complexResult.length / 2];  // Use half because of symmetry in FFT output
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = complexResult[i].abs();
        }
        return magnitudes;
    }

    private float findFundamentalFrequency(double[] magnitudes) {
        int maxIndex = -1;
        double maxMagnitude = Double.NEGATIVE_INFINITY;
        // Skip the DC component (index 0) and find the index with the maximum magnitude
        for (int i = 1; i < magnitudes.length; i++) {
            if (magnitudes[i] > maxMagnitude) {
                maxMagnitude = magnitudes[i];
                maxIndex = i;
            }
        }
        System.out.println("Max index : " + maxIndex);
        // Calculate the fundamental frequency based on the index and sample rate
        float fundamentalFrequency = -1.0f;
        if (maxIndex != -1) {
            fundamentalFrequency =(float) maxIndex * SAMPLE_RATE / FFT_SIZE;
        }

        return fundamentalFrequency;
    }
}
