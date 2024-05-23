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
    
    private Queue<Float> accelerationBuffer = new LinkedList<>();
    private String name = "";
    private int samplingFrequency = 100;  
    private int fftSize = 1024;           
    private float time = 0f;
    private int steps = 0;              

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        JSONObject jsonObject = parseRequestToJson(request);
        updateServiceState(jsonObject);
        processAccelerationData(jsonObject.getJSONArray("verticalAccelerations"));
        steps = calculateSteps(accelerationBuffer, time, samplingFrequency, fftSize);

        JSONObject responseJson = createResponseJson();
        sendResponse(response, responseJson);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        JSONObject responseJson = createResponseJson();
        sendResponse(response, responseJson);
    }

    /**
     * @param request Requête HTTP contenant les données JSON
     * @return JSONObject représentant les données de la requête
     * @throws IOException En cas d'erreur de lecture de la requête
     */
    private JSONObject parseRequestToJson(HttpServletRequest request) throws IOException {
        StringBuilder jsonBuffer = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                jsonBuffer.append(line);
            }
        }
        return new JSONObject(jsonBuffer.toString());
    }

    /**
     * Met à jour l'état du service avec les données reçues
     * @param jsonObject Objet JSON contenant les nouvelles données
     */
    private void updateServiceState(JSONObject jsonObject) {
        time = jsonObject.getFloat("time");
        samplingFrequency = jsonObject.getInt("samplingFrequency");
        fftSize = jsonObject.getInt("fftSize");
        name = jsonObject.getString("name");
    }

    /**
     * Traite les données d'accélération et met à jour le buffer d'accélération
     * @param verticalAccelerations Tableau JSON des accélérations verticales
     */
    private void processAccelerationData(JSONArray verticalAccelerations) {
        accelerationBuffer.clear();
        for (int i = 0; i < verticalAccelerations.length(); i++) {
            float acceleration = verticalAccelerations.getFloat(i);
            if (accelerationBuffer.size() >= fftSize) {
                accelerationBuffer.poll();
            }
            accelerationBuffer.add(acceleration);
        }
    }

    /**
     * Crée un objet JSON contenant les informations de réponse
     * @return JSONObject représentant la réponse
     */
    private JSONObject createResponseJson() {
        JSONObject responseJson = new JSONObject();
        responseJson.put("name", name);
        responseJson.put("samplingFrequency", samplingFrequency);
        responseJson.put("fftSize", fftSize);
        responseJson.put("time", time);
        responseJson.put("steps", steps);
        responseJson.put("verticalAccelerationBuffer", new JSONArray(accelerationBuffer));
        return responseJson;
    }

    /**
     * Envoie la réponse au client
     * @param response Réponse HTTP
     * @param responseJson Objet JSON à envoyer dans la réponse
     * @throws IOException En cas d'erreur lors de l'envoi de la réponse
     */
    private void sendResponse(HttpServletResponse response, JSONObject responseJson) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.print(responseJson.toString());
        out.flush();
    }

    /**
     * Calcule le nombre de pas à partir des données d'accélération
     * @param accelerationBuffer Buffer contenant les accélérations
     * @param time Temps écoulé
     * @param sampleRate Taux d'échantillonnage en Hz
     * @param fftSize Taille de la FFT
     * @return Nombre de pas calculé
     */
    private int calculateSteps(Queue<Float> accelerationBuffer, float time, int sampleRate, int fftSize) {
        if (accelerationBuffer.size() < fftSize) {
            return 0;
        }

        float[] accelArray = convertQueueToArray(accelerationBuffer);
        removeDCComponent(accelArray);
        double[] magnitudes = performFFT(accelArray);
        int maxIndex = findMaxMagnitudeIndex(magnitudes, sampleRate, fftSize);
        return computeSteps(maxIndex, sampleRate, fftSize, time);
    }

    /**
     * Convertit le buffer d'accélération en tableau
     * @param buffer Buffer de données d'accélération
     * @return Tableau de données d'accélération
     */
    private float[] convertQueueToArray(Queue<Float> buffer) {
        float[] array = new float[buffer.size()];
        int index = 0;
        for (Float value : buffer) {
            array[index++] = (value != null ? value : 0.0f);
        }
        return array;
    }

    /**
     * Supprime la composante continue (DC) des données
     * @param data Tableau de données d'accélération
     */
    private void removeDCComponent(float[] data) {
        double mean = calculateMean(data);
        for (int i = 0; i < data.length; i++) {
            data[i] -= mean;
        }
    }

    /**
     * Effectue la FFT sur les données d'entrée
     * @param input Tableau de données d'entrée
     * @return Tableau de magnitudes des fréquences
     */
    private double[] performFFT(float[] input) {
        // Initialiser l'objet FastFourierTransformer avec la normalisation standard
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        
        // Convertir le tableau d'entrée en double
        double[] inputDouble = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            inputDouble[i] = input[i];
        }
        
        // Effectuer la transformation FFT avant sur le tableau d'entrée
        Complex[] complexResult = fft.transform(inputDouble, TransformType.FORWARD);
        
        // Extraire les magnitudes des fréquences (utiliser la moitié de la sortie FFT à cause de la symétrie)
        double[] magnitudes = new double[complexResult.length / 2];
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = complexResult[i].abs();
        }
        return magnitudes;
    }

    /**
     * Trouve l'indice de la magnitude maximale dans le tableau de magnitudes
     * @param magnitudes Tableau de magnitudes des fréquences
     * @param sampleRate Taux d'échantillonnage en Hz
     * @param fftSize Taille de la FFT
     * @return Indice de la magnitude maximale
     */
    private int findMaxMagnitudeIndex(double[] magnitudes, int sampleRate, int fftSize) {
        int maxIndex = -1;
        double maxMagnitude = Double.NEGATIVE_INFINITY;
        
        // Ignorer les fréquences inférieures à 1 Hz et supérieures à 3 Hz
        int minIndex = (int) Math.ceil(1.0 * fftSize / sampleRate);
        int maxIndexLimit = (int) Math.floor(3.0 * fftSize / sampleRate);
        for (int i = minIndex; i <= maxIndexLimit; i++) {
            if (magnitudes[i] > maxMagnitude) {
                maxMagnitude = magnitudes[i];
                maxIndex = i;
            }
        }
        
        // Imprimer les informations de débogage
        System.out.println("Indice maximal: " + maxIndex);
        System.out.println("Magnitude maximale: " + maxMagnitude);
        
        return maxIndex;
    }

    /**
     * Calcule le nombre de pas à partir de la fréquence fondamentale
     * @param maxIndex Indice de la magnitude maximale
     * @param sampleRate Taux d'échantillonnage en Hz
     * @param fftSize Taille de la FFT
     * @param time Temps écoulé
     * @return Nombre de pas calculé
     */
    private int computeSteps(int maxIndex, int sampleRate, int fftSize, float time) {
        // Calculer la fréquence fondamentale à partir de l'indice et du taux d'échantillonnage
        float fundamentalFrequency = (float) maxIndex * sampleRate / fftSize;
        System.out.println("Fréquence fondamentale: " + fundamentalFrequency);
        
        // Calculer la cadence (nombre de pas par seconde) et le nombre de pas total
        float cadence = fundamentalFrequency;
        int calculatedSteps = (int) (cadence * time);
        System.out.println("Nombre de pas calculé: " + calculatedSteps);
        
        return calculatedSteps;
    }

    /**
     * Calcule la moyenne des données
     * @param data Tableau de données
     * @return Moyenne des données
     */
    private double calculateMean(float[] data) {
        double sum = 0;
        for (float value : data) {
            sum += value;
        }
        return sum / data.length;
    }
}
