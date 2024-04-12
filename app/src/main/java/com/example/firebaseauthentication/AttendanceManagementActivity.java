package com.example.firebaseauthentication;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceManagementActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private static final int REQUEST_CHECK_SETTINGS = 2;
    private static double LOCATION_RANGE_METERS = 20; // Define the range in meters
    private static double TARGET_ALTITUDE = -46.0;
    private static double ALTITUDE_TOLERANCE = 10.0;
    String currentMonthYear = getCurrentMonthYear();
    String currentDate = getCurrentDate(); // Get current date without time
    String currentTime = getCurrentTime();
    Double latitude = 0.0;
    Double longitude = 0.0;
    Button stopAttendanceButton, saveRecordsButton, addStudent, btnCheckRange, SetRadius;
    FirebaseFirestore db;
    boolean locationCallbackEnabled = true;
    private Polygon circlePolygon;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private MapView mapView;
    private TextView tvAltitude, counter;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private RecyclerView recyclerView, recyclerViewAddStudent;
    private List<String> studentsList;
    private AttendanceAdapter adapter;
    private AddStudentAdapter addStudentAdapter;
    private String joinCode;
    private ListenerRegistration attendanceListener;
    private CountDownTimer countDownTimer;
    private LinearLayout mapContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_management);

        if (isDeveloperModeEnabled()) {
            // USB debugging is enabled, take appropriate action (e.g., show a message and exit the app)
            Toast.makeText(this, "Disable Developer Mode to Use BAMS.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            return;
        }



        checkPermissions();

        mapContent = findViewById(R.id.mapContent);
        mapView = findViewById(R.id.mapView);
        btnCheckRange = findViewById(R.id.btnCheckRange);
        tvAltitude = findViewById(R.id.tvAltitude);
        counter = findViewById(R.id.counter);
        Button btnCalibrate = findViewById(R.id.btnRecalibrate); // Find the "Calibrate" button

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set initial map center and zoom level
        mapView.getController().setCenter(new GeoPoint(latitude, longitude));
        mapView.getController().setZoom(20.0);

        // Create a new MyLocation overlay with high accuracy
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(AttendanceManagementActivity.this), mapView);
        myLocationOverlay.enableMyLocation(); // Enable showing the current location
        myLocationOverlay.enableFollowLocation(); // Enable following the current location
        myLocationOverlay.setDrawAccuracyEnabled(true); // Draw a circle indicating location accuracy
        mapView.getOverlayManager().add(myLocationOverlay);

        // Set listener for location updates
        myLocationOverlay.runOnFirstFix(() -> {
            runOnUiThread(() -> {
                centerMapOnCurrentLocation();
            });
        });


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Use Geo-Fencing");
        builder.setMessage("Do you want to use Geo-Fencing?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                mapContent.setVisibility(View.VISIBLE);

                FirebaseFirestore db = FirebaseFirestore.getInstance();
                DocumentReference attendanceDocRef = db.collection("classes")
                        .document(joinCode)
                        .collection("attendance_records")
                        .document(currentMonthYear);

                attendanceDocRef.get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                // Document exists, update the field
                                attendanceDocRef.update("GeoFence", true)
                                        .addOnSuccessListener(aVoid -> showToast("Geo-Fence Enabled"))
                                        .addOnFailureListener(e -> showToast("Error enabling Geo-Fence"));
                            }
                        })
                        .addOnFailureListener(e -> showToast("Failed to fetch attendance geo fence status"));

                countDownTimer = new CountDownTimer(20000, 1000) {
                    public void onTick(long millisUntilFinished) {
                        // Show a toast indicating the countdown
                        counter.setText("Location updates will start in " + millisUntilFinished / 1000 + " seconds");
                        saveRecordsButton.setEnabled(false);
                        btnCalibrate.setEnabled(false);
                        stopAttendanceButton.setEnabled(false);
                        addStudent.setEnabled(false);
                    }

                    public void onFinish() {
                        counter.setVisibility(View.GONE);
                        // Start location updates after the timer finishes

                        initializeLocationCallback();
                        initializeMapView();


                        // Initialize location callback
                        startLocationUpdates();
                        centerMapOnCurrentLocation();
                        countDownTimer = null;
                        saveRecordsButton.setEnabled(true);
                        btnCalibrate.setEnabled(true);
                        stopAttendanceButton.setEnabled(true);
                        addStudent.setEnabled(true);
                    }
                }.start();

                // Initialize location callback
                // User confirmed to use Geo-Fencing
                // You can add the functionality for Geo-Fencing here
                // For now, just dismiss the dialog
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                FirebaseFirestore db = FirebaseFirestore.getInstance();
                DocumentReference attendanceDocRef = db.collection("classes")
                        .document(joinCode)
                        .collection("attendance_records")
                        .document(currentMonthYear);

                attendanceDocRef.get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                // Document exists, update the field
                                attendanceDocRef.update("GeoFence", false)
                                        .addOnSuccessListener(aVoid -> showToast("Geo-Fence Disabled"))
                                        .addOnFailureListener(e -> showToast("Error disabling Geo-Fence"));
                            }
                        })
                        .addOnFailureListener(e -> showToast("Failed to fetch attendance geo fence status"));

                // User chose not to use Geo-Fencing
                // You can add the functionality for not using Geo-Fencing here
                // For now, just dismiss the dialog
                initializeLocationCallback();
                dialog.dismiss();
            }
        });
        // Show the dialog
        builder.show();

        btnCheckRange.setOnClickListener(v -> checkRange());

        // Set listener for the "Calibrate" button
        btnCalibrate.setOnClickListener(v -> {
            centerMapOnCurrentLocation();
            startCarrierPhaseTracking();
        });

        db = FirebaseFirestore.getInstance();

        joinCode = getIntent().getStringExtra("joinCode");

        recyclerView = findViewById(R.id.recyclerViewAttendance);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(AttendanceManagementActivity.this, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);

        stopAttendanceButton = findViewById(R.id.stopAttendanceButton);
        saveRecordsButton = findViewById(R.id.saveRecordsButton);
        addStudent = findViewById(R.id.addStudent);
        studentsList = new ArrayList<>();

        fetchStudents();

        addStudent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStudentListDialog();
            }
        });

        stopAttendanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAttendance();
            }
        });

        saveRecordsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                FirebaseFirestore db = FirebaseFirestore.getInstance();
                DocumentReference attendanceDocRef = db.collection("classes")
                        .document(joinCode)
                        .collection("attendance_records")
                        .document(currentMonthYear);

                attendanceDocRef.get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                // Document exists, update the field
                                attendanceDocRef.update("GeoFence", false)
                                        .addOnSuccessListener(aVoid -> showToast("Geo-Fence Enabled"))
                                        .addOnFailureListener(e -> showToast("Error enabling Geo-Fence"));
                            }
                        })
                        .addOnFailureListener(e -> showToast("Failed to fetch attendance geo fence status"));

                saveRecords();
            }
        });

        fetchAttendanceRecords();
    }

    @Override
    public void onBackPressed() {
        if (countDownTimer != null) {
            // Timer is still running, show toast and prevent back press
            Toast.makeText(this, "Cannot exit when timer is working", Toast.LENGTH_SHORT).show();
        } else {
            // Timer has finished, allow back press
            super.onBackPressed();
            stopRealtimeLocation();
        }
    }

    private void initializeLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {

                if (!locationCallbackEnabled || locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    // Handle location updates here
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    double altitude = location.getAltitude();
                    // Update UI or perform other actions with the obtained location data
                    // For example:
                    updateUI(latitude, longitude, altitude);
                    centerMapOnCurrentLocation();
                    // Remove previous circle overlay
                    mapView.getOverlayManager().remove(circlePolygon);
                    // Add new circle overlay
                    addCircleOverlay(latitude, longitude, LOCATION_RANGE_METERS);

                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    DocumentReference attendanceDocRef = db.collection("classes")
                            .document(joinCode)
                            .collection("attendance_records")
                            .document(currentMonthYear);

                    attendanceDocRef.get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    // Document exists, update the field
                                    attendanceDocRef.update("Altitude", altitude, "Latitude", latitude, "Longitude", longitude);
                                } else {
                                    // Document doesn't exist, create it with classesConducted set to 1
                                    Map<String, Object> data = new HashMap<>();
                                    data.put("classesConducted", 1);
                                    attendanceDocRef.set(data)
                                            .addOnSuccessListener(aVoid -> showToast("Attendance records created successfully"))
                                            .addOnFailureListener(e -> showToast("Failed to create attendance records"));
                                }
                            })
                            .addOnFailureListener(e -> showToast("Failed to fetch attendance records"));
                }

            }

        };
    }

    // Method to stop location updates
    private void stopRealtimeLocation() {
        locationCallbackEnabled = false;
        showToast("Realtime Location Disabled");
    }

    private void startCarrierPhaseTracking() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Handle location update
                // Extract carrier phase information
                if (location.hasAccuracy() && location.hasAltitude()) {
                    float accuracy = location.getAccuracy();
                    double altitude = location.getAltitude();
                    // Implement processing of carrier phase information here
                    // This could include RTK or PPP algorithms to calculate highly accurate positions
                } else {
                    showToast("Accuracy or altitude information not available");
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // Not used in this example
            }

            @Override
            public void onProviderEnabled(String provider) {
                // Not used in this example
            }

            @Override
            public void onProviderDisabled(String provider) {
                // Not used in this example
            }
        };
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    30000, // Minimum time interval between location updates, in milliseconds
                    0, // Minimum distance between location updates, in meters
                    locationListener);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AttendanceManagementActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopCarrierPhaseTracking() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }


    private void initializeMapView() {
        if (isNetworkAvailable()) {
            mapView.setTileSource(TileSourceFactory.MAPNIK);
        } else {
            mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        }
        mapView.getController().setCenter(new GeoPoint(latitude, longitude));
        mapView.getController().setZoom(12.0);
        mapView.setMultiTouchControls(true);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void updateParameters(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private void centerMapOnCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            GeoPoint currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                            mapView.getController().animateTo(currentLocation);
                        } else {
                            Toast.makeText(AttendanceManagementActivity.this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkRange() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            double distance = calculateDistance(location.getLatitude(), location.getLongitude(),
                                    latitude, longitude);

                            double altitude = location.getAltitude();

                            tvAltitude.setText("Altitude: " + altitude + " meters\nLatitude: " + latitude + "\nLongitude: " + longitude);

                            double altitudeDifference = Math.abs(altitude - TARGET_ALTITUDE);

                            if (distance <= LOCATION_RANGE_METERS && altitudeDifference <= ALTITUDE_TOLERANCE) {
                                Toast.makeText(AttendanceManagementActivity.this, "Within range and altitude", Toast.LENGTH_SHORT).show();
                            } else {
                                if (distance > LOCATION_RANGE_METERS) {
                                    Toast.makeText(AttendanceManagementActivity.this, "Outside range", Toast.LENGTH_SHORT).show();
                                }
                                if (altitudeDifference > ALTITUDE_TOLERANCE) {
                                    Toast.makeText(AttendanceManagementActivity.this, "Outside altitude", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            Toast.makeText(AttendanceManagementActivity.this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371e3;
        double φ1 = Math.toRadians(lat1);
        double φ2 = Math.toRadians(lat2);
        double Δφ = Math.toRadians(lat2 - lat1);
        double Δλ = Math.toRadians(lon2 - lon1);
        double a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) *
                        Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void addCircleOverlay(double latitude, double longitude, double radius) {
        final int numEdges = 64;
        List<GeoPoint> circlePoints = new ArrayList<>();
        for (int i = 0; i < numEdges; i++) {
            double angle = Math.toRadians(i * 360.0 / numEdges);
            double circleLat = latitude + radius / 111000.0 * Math.cos(angle);
            double circleLon = longitude + radius / (111000.0 * Math.cos(Math.toRadians(latitude))) * Math.sin(angle);
            circlePoints.add(new GeoPoint(circleLat, circleLon));
        }
        circlePolygon = new Polygon(mapView);
        circlePolygon.setFillColor(Color.parseColor("#500106c1"));
        circlePolygon.setStrokeColor(Color.parseColor("#3453cb"));
        circlePolygon.setStrokeWidth(6);
        circlePolygon.getOutlinePaint().setPathEffect(new DashPathEffect(new float[]{30, 20}, 0));
        circlePolygon.setPoints(circlePoints);
        mapView.getOverlayManager().add(circlePolygon);
        mapView.invalidate();
    }

    // Check and request permissions
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request the permission
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    // Handle the permission request response
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with the operation that requires this permission
                // You can initiate your storage write operation here
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied, handle accordingly (e.g., show a message or disable functionality)
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.requestLocationUpdates(getLocationRequest(), locationCallback, null);
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private LocationRequest getLocationRequest() {
        return LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000) // Update location every 10 seconds (adjust as needed)
                .setFastestInterval(5000); // Set the fastest interval for location updates
    }

    private void updateUI(double latitude, double longitude, double altitude) {
        // Update UI components with the obtained location data
        // For example:
        tvAltitude.setText("Altitude: " + altitude + " meters\nLatitude: " + latitude + "\nLongitude: " + longitude);
    }


    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        stopRealtimeLocation();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopLocationUpdates();
        stopRealtimeLocation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove Firestore listener when activity is destroyed
        if (attendanceListener != null) {
            attendanceListener.remove();
        }
        stopLocationUpdates();
        stopRealtimeLocation();
    }

    private void fetchAttendanceRecords() {
        CollectionReference attendanceRef = FirebaseFirestore.getInstance()
                .collection("classes")
                .document(joinCode)
                .collection("attendance_records")
                .document(new SubjectDetailsActivity().getCurrentMonthYear())
                .collection("temp_records");

        attendanceListener = attendanceRef.addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                Log.e("AttendanceManagement", "Listen failed.", e);
                return;
            }

            List<DocumentSnapshot> attendanceSnapshots = queryDocumentSnapshots.getDocuments();
            List<Map<String, Object>> attendanceData = new ArrayList<>();

            for (DocumentSnapshot snapshot : attendanceSnapshots) {
                // Fetch the student's name from the document ID
                String studentName = snapshot.getId();
                Map<String, Object> attendanceRecord = snapshot.getData();
                // Add student name to the data
                attendanceRecord.put("attendeeName", studentName);
                attendanceData.add(attendanceRecord);
            }

            // Update the RecyclerView with the new data
            adapter = new AttendanceAdapter(AttendanceManagementActivity.this, attendanceData, joinCode); // Pass joinCode
            recyclerView.setAdapter(adapter);
        });
    }

    private void stopAttendance() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference classDocRef = db.collection("classes").document(joinCode);

        classDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                boolean attendance = documentSnapshot.getBoolean("attendance");

                // Toggle the attendance field value
                classDocRef.update("attendance", !attendance)
                        .addOnSuccessListener(aVoid -> {
                            if (!attendance) {
                                // Attendance was resumed, change button text to "Pause Attendance"
                                Toast.makeText(AttendanceManagementActivity.this, "Attendance marking resumed", Toast.LENGTH_SHORT).show();
                                stopAttendanceButton.setText("Pause");
                            } else {
                                // Attendance was paused, change button text to "Resume Attendance"
                                Toast.makeText(AttendanceManagementActivity.this, "Attendance marking paused", Toast.LENGTH_SHORT).show();
                                stopAttendanceButton.setText("Resume");
                            }
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(AttendanceManagementActivity.this, "Failed to update attendance status", Toast.LENGTH_SHORT).show();
                            Log.e("AttendanceManagement", "Error updating attendance status", e);
                        });
            } else {
                Toast.makeText(AttendanceManagementActivity.this, "Class document does not exist", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(AttendanceManagementActivity.this, "Failed to fetch class document", Toast.LENGTH_SHORT).show();
            Log.e("AttendanceManagement", "Error fetching class document", e);
        });
    }


    private void saveRecords() {

        if (joinCode == null || currentMonthYear == null) {
            showToast("Join code, current month/year, or current date is null");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference tempRecordsRef = db.collection("classes")
                .document(joinCode)
                .collection("attendance_records")
                .document(currentMonthYear)
                .collection("temp_records");

        // Check if there are any documents in temp_records
        tempRecordsRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots.isEmpty()) {
                showToast("No records were marked");
                setAttendanceStatus(false); // Set attendance to false when no records are marked
                finish(); // Finish the activity if no records were marked
            } else {
                // Proceed with saving records
                CollectionReference currentMonthYearRef = db.collection("classes")
                        .document(joinCode)
                        .collection("attendance_records")
                        .document(currentMonthYear)
                        .collection(currentDate); // Create a collection for the current date

                // Fetch and store each document individually
                List<DocumentReference> documentReferences = new ArrayList<>();
                List<Object> dataList = new ArrayList<>(); // Create a list to store all student data

                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    String userName = document.getId();
                    String name = (String) document.get("Name");
                    String markingTime = (String) document.get("markingTime");

                    // Store markingTime and name as elements of an array
                    List<Object> studentData = new ArrayList<>();
                    studentData.add(markingTime);
                    studentData.add(name);

                    // Add each student's data to the list
                    dataList.add(studentData);

                    // Store document reference for deletion after loop completion
                    documentReferences.add(tempRecordsRef.document(userName));
                }

                // Store data list in a new document with a unique field name (currentTime)
                DocumentReference newDocumentRef = currentMonthYearRef.document(currentTime);
                newDocumentRef.set(createDataMap(dataList))
                        .addOnSuccessListener(aVoid -> {
                            showToast("Document created in current date collection successfully");
                            incrementClassesConducted(joinCode, currentMonthYear);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            String errorMessage = "Failed to create document in current date collection: " + e.getMessage();
                            showToast(errorMessage);
                            Log.e("Firestore Creation Error", errorMessage, e); // Logging the error
                        });

                // Delete all documents from temp_records
                deleteTempRecords(documentReferences);

                stopAttendance(); // Stop attendance
            }
        }).addOnFailureListener(e -> {
            showToast("Failed to fetch documents from temp_records: " + e.getMessage());
        });
    }

    // Helper method to set the attendance status in Firestore
    private void setAttendanceStatus(boolean status) {
        DocumentReference classDocRef = db.collection("classes").document(joinCode);

        classDocRef.update("attendance", status)
                .addOnSuccessListener(aVoid -> {
                    if (!status) {
                        showToast("Attendance marking paused");
                        stopAttendanceButton.setText("Resume Attendance");
                    } else {
                        showToast("Attendance marking resumed");
                        stopAttendanceButton.setText("Pause Attendance");
                    }
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to update attendance status: " + e.getMessage());
                    Log.e("AttendanceManagement", "Error updating attendance status", e);
                });
    }

    // Helper method to create a map with usernames as keys and data lists as values
    private Map<String, Object> createDataMap(List<Object> dataList) {
        Map<String, Object> dataMap = new HashMap<>();
        int index = 0;
        for (Object data : dataList) {
            dataMap.put(((List<Object>) data).get(1).toString(), data); // Assuming username is at index 1 in the data list
            index++;
        }
        return dataMap;
    }


    // Helper method to delete all documents from temp_records collection
    private void deleteTempRecords(List<DocumentReference> documentReferences) {
        for (DocumentReference documentReference : documentReferences) {
            documentReference.delete()
                    .addOnSuccessListener(aVoid -> {
                        showToast("Document deleted from temp_records successfully");
                    })
                    .addOnFailureListener(e -> {
                        showToast("Failed to delete document from temp_records: " + e.getMessage());
                    });
        }
    }

    private void incrementClassesConducted(String joinCode, String currentMonthYear) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference attendanceDocRef = db.collection("classes")
                .document(joinCode)
                .collection("attendance_records")
                .document(currentMonthYear);

        attendanceDocRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Document exists, update the field
                        int classesConducted = documentSnapshot.getLong("classesConducted").intValue();
                        classesConducted++; // Increment the value
                        attendanceDocRef.update("classesConducted", classesConducted)
                                .addOnSuccessListener(aVoid -> showToast("Attendance records updated successfully"))
                                .addOnFailureListener(e -> showToast("Failed to update attendance records"));
                    } else {
                        // Document doesn't exist, create it with classesConducted set to 1
                        Map<String, Object> data = new HashMap<>();
                        data.put("classesConducted", 1);
                        attendanceDocRef.set(data)
                                .addOnSuccessListener(aVoid -> showToast("Attendance records created successfully"))
                                .addOnFailureListener(e -> showToast("Failed to create attendance records"));
                    }
                })
                .addOnFailureListener(e -> showToast("Failed to fetch attendance records"));
    }


    private void fetchStudents() {
        db.collection("classes").document(joinCode).collection("students")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        studentsList.clear();
                        for (DocumentSnapshot document : task.getResult()) {
                            String studentName = document.getString("name");
                            studentsList.add(studentName);
                        }
                        // No need to call showStudentListDialog here
                    } else {
                        // Handle errors
                    }
                });
    }

    private void showStudentListDialog() {
        // Fetch students when dialog is created
        fetchStudents();

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_student, null);
        dialogBuilder.setView(dialogView);

        RecyclerView recyclerViewStudents = dialogView.findViewById(R.id.recyclerViewStudents);
        recyclerViewStudents.setLayoutManager(new LinearLayoutManager(this));
        addStudentAdapter = new AddStudentAdapter(studentsList, this);
        recyclerViewStudents.setAdapter(addStudentAdapter);

        // Add cancel button
        dialogBuilder.setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss());

        // Add add button
        dialogBuilder.setPositiveButton("Add", (dialogInterface, i) -> {
            // Perform add action here if needed
            // For example, you can add selected students to the class
            addSelectedStudentsToTempRecords();
        });

        AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void addSelectedStudentsToTempRecords() {
        // Get the list of selected students from the adapter
        List<String> selectedStudents = addStudentAdapter.getSelectedStudents();

        // Check if any students are selected
        if (selectedStudents.isEmpty()) {
            showToast("No students selected");
            return;
        }

        // Get current marking time
        String currentTime = getCurrentDateTime();

        // Store the records in the temp_records collection
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference tempRecordsRef = db.collection("classes")
                .document(joinCode)
                .collection("attendance_records")
                .document(getCurrentMonthYear())
                .collection("temp_records");

        // Iterate through the selected students and add them to temp_records collection
        for (String studentName : selectedStudents) {
            // Create a map to store the data for this student
            Map<String, Object> attendanceData = new HashMap<>();
            attendanceData.put("Name", studentName); // Name field is the student's name
            attendanceData.put("markingTime", currentTime); // markingTime field is the current time

            // Store the data in a document named after the studentName
            tempRecordsRef.document(studentName)
                    .set(attendanceData)
                    .addOnSuccessListener(aVoid -> {
                        showToast("Attendance marked for " + studentName);
                    })
                    .addOnFailureListener(e -> {
                        showToast("Failed to mark attendance for " + studentName + ": " + e.getMessage());
                    });
        }
    }

    private void moveDocument(DocumentReference sourceDocumentRef, Map<String, Object> data,
                              FirebaseFirestore db, String joinCode, String currentMonthYear,
                              String currentDateTimeDay) {
        DocumentReference destinationDocumentRef = db.collection("classes")
                .document(joinCode)
                .collection("attendance_records")
                .document(currentMonthYear)
                .collection(currentDateTimeDay)
                .document((String) data.get("attendeeName"));

        // Move the document by copying its data to the destination document
        destinationDocumentRef.set(data)
                .addOnSuccessListener(aVoid -> {
                    // Document successfully moved, now delete it from temp_records
                    sourceDocumentRef.delete()
                            .addOnSuccessListener(aVoid1 -> {
                                showToast("Document moved and deleted successfully");
                                incrementClassesConducted(joinCode, currentMonthYear);
                            })
                            .addOnFailureListener(e -> {
                                showToast("Failed to delete document from temp_records: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    showToast("Failed to move document: " + e.getMessage());
                });
    }

    private void deleteTempRecordsCollection(CollectionReference tempRecordsRef) {
        tempRecordsRef.get().addOnSuccessListener(querySnapshot -> {
            for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                document.getReference().delete()
                        .addOnSuccessListener(aVoid -> {
                            showToast("Document deleted successfully");
                        })
                        .addOnFailureListener(e -> {
                            showToast("Failed to delete document: " + e.getMessage());
                        });
            }
        }).addOnFailureListener(e -> {
            showToast("Failed to retrieve documents for deletion: " + e.getMessage());
        });
    }

    private String getCurrentDate() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    private String getCurrentTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh-mm-ss a", Locale.getDefault());
        return timeFormat.format(calendar.getTime());
    }


    // Helper method to get the current date, time, and day
    String getCurrentMonthYear() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM_yyyy", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    private String getCurrentDateTime() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss a", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    private boolean isDeveloperModeEnabled() {
        return Settings.Secure.getInt(getApplicationContext().getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
    }

}
