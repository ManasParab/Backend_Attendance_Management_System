package com.example.firebaseauthentication;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

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
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubjectDetailsActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private static final int REQUEST_CHECK_SETTINGS = 2;
    private static double LOCATION_RANGE_METERS = 20; // Define the range in meters
    private static double Altitude = -46.0;
    private static double ALTITUDE_TOLERANCE = 10.0;
    Double latitude = 0.0;
    Double longitude = 0.0;
    MaterialButton chkStudentsButton, markAttendance, newAttendance, chkAttendance, myAttendance, manageAccess, getDefaulter;
    private Polygon circlePolygon;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private MapView mapView;
    private Button btnCheckRange, SetRadius;
    private TextView tvAltitude, counter, fetchedCoordinates;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private TextView tvSubjectName, tvJoiningCode;
    private String userRole, joinCode, subjectName; // Variable to store user's role

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_details);

        if (isDeveloperModeEnabled()) {
            // USB debugging is enabled, take appropriate action (e.g., show a message and exit the app)
            Toast.makeText(this, "Disable Developer Mode to Use BAMS.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            return;
        }

// Check and request permissions
        checkPermissions();

        // Initialize osmdroid configuration
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));

        mapView = findViewById(R.id.mapView);
        btnCheckRange = findViewById(R.id.btnCheckRange);
        SetRadius = findViewById(R.id.SetRadius);
        tvAltitude = findViewById(R.id.tvAltitude);
        counter = findViewById(R.id.counter);
        fetchedCoordinates = findViewById(R.id.fetchedCoordinates);
        Button btnCalibrate = findViewById(R.id.btnRecalibrate); // Find the "Calibrate" button

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize location callback
        initializeLocationCallback();

        initializeMapView();

        SetRadius.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateParameters(latitude, longitude);
            }
        });

        // Set initial map center and zoom level

        mapView.getController().setCenter(new GeoPoint(latitude, longitude));
        mapView.getController().setZoom(20.0);

        // Create a new MyLocation overlay with high accuracy
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation(); // Enable showing the current location
        myLocationOverlay.enableFollowLocation(); // Enable following the current location
        myLocationOverlay.setDrawAccuracyEnabled(true); // Draw a circle indicating location accuracy
        mapView.getOverlayManager().add(myLocationOverlay);

        myLocationOverlay.enableFollowLocation();

        // Set listener for location updates
        myLocationOverlay.runOnFirstFix(() -> {
            runOnUiThread(() -> {
                centerMapOnCurrentLocation();
            });
        });

        btnCheckRange.setOnClickListener(v -> checkRange());

        // Set listener for the "Calibrate" button
        btnCalibrate.setOnClickListener(v -> {
            centerMapOnCurrentLocation();
            startCarrierPhaseTracking();
        });

        tvSubjectName = findViewById(R.id.tvSubjectName);
        tvJoiningCode = findViewById(R.id.tvJoiningCode);

        chkStudentsButton = findViewById(R.id.chkStudents);
        newAttendance = findViewById(R.id.newAttendance);
        markAttendance = findViewById(R.id.markAttendance);
        chkAttendance = findViewById(R.id.chkAttendance);
        myAttendance = findViewById(R.id.myAttendance);
        manageAccess = findViewById(R.id.manageAccess);
        getDefaulter = findViewById(R.id.getDefaulter);

        // Retrieve data from intent
        subjectName = getIntent().getStringExtra("subjectName");
        joinCode = getIntent().getStringExtra("joinCode");

        // Set data to views
        tvSubjectName.setText(subjectName);
        tvJoiningCode.setText(joinCode);

        // Fetch user's role from Firestore
        fetchUserRole();

        // Set click listener for newAttendance button
        // Set click listener for newAttendance button
        newAttendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show confirmation dialog before starting attendance
                showConfirmationDialog();
            }
        });


        // Method to show confirmation dialog before starting attendance
        markAttendance.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View v) {

                if (isDeveloperModeEnabled()) {
                    // USB debugging is enabled, take appropriate action (e.g., show a message and exit the app)
                    showToast("Disable Developer Mode to Mark Attendance.");
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                    return;
                }
                markAttendance();
            }
        });

        // Inside onCreate() method of SubjectDetailsActivity.java

        chkAttendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start TeacherAttendanceRecordsActivity and pass the joinCode via intent
                Intent intent = new Intent(SubjectDetailsActivity.this, TeacherAttendanceRecordsActivity.class);
                intent.putExtra("joinCode", joinCode); // Pass the joinCode from the TextView
                startActivity(intent);
            }
        });


        // Set click listener for chkStudents button
        chkStudentsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start StudentsEnrolledActivity and pass the joinCode via intent
                Intent intent = new Intent(SubjectDetailsActivity.this, StudentsEnrolledActivity.class);
                intent.putExtra("joinCode", joinCode); // Pass the joinCode from the TextView
                startActivity(intent);
            }
        });

        myAttendance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myAttendance();

            }
        });

        manageAccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SubjectDetailsActivity.this, manageAccessActivity.class);
                intent.putExtra("joinCode", joinCode);
                intent.putExtra("subjectName", subjectName);
                startActivity(intent);
            }
        });

        getDefaulter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchAndExportDataToCSV();
            }
        });

    }


    private void initializeLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    // Handle location updates here
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    Altitude = location.getAltitude();
                    // Update UI or perform other actions with the obtained location data
                    // For example:
                    updateUI(latitude, longitude, Altitude);
                    centerMapOnCurrentLocation();
                    // Remove previous circle overlay
                    mapView.getOverlayManager().remove(circlePolygon);
                    // Add new circle overlay
                    addCircleOverlay(latitude, longitude, LOCATION_RANGE_METERS);
                }
            }
        };
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
                    100, // Minimum time interval between location updates, in milliseconds
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
                Toast.makeText(SubjectDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(SubjectDetailsActivity.this, "Unable to get current location", Toast.LENGTH_SHORT).show();
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

                            double altitudeDifference = Math.abs(altitude - Altitude);

                            if (distance <= LOCATION_RANGE_METERS && altitudeDifference <= ALTITUDE_TOLERANCE) {
                                Toast.makeText(SubjectDetailsActivity.this, "Within range and altitude", Toast.LENGTH_SHORT).show();
                            } else {
                                if (distance > LOCATION_RANGE_METERS) {
                                    Toast.makeText(SubjectDetailsActivity.this, "Outside range", Toast.LENGTH_SHORT).show();
                                }
                                if (altitudeDifference > ALTITUDE_TOLERANCE) {
                                    Toast.makeText(SubjectDetailsActivity.this, "Outside altitude", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            Toast.makeText(SubjectDetailsActivity.this, "Unable to get current location", Toast.LENGTH_SHORT).show();
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
        circlePolygon.setFillColor(Color.parseColor("#700106c1"));
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

    @Override
    protected void onStart() {
        super.onStart();
        startLocationUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopLocationUpdates();
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


    private void fetchUserRole() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(this::onUserRoleSuccess)
                    .addOnFailureListener(e -> Toast.makeText(SubjectDetailsActivity.this, "Error fetching user data", Toast.LENGTH_SHORT).show());
        }
    }

    private void onUserRoleSuccess(DocumentSnapshot documentSnapshot) {
        if (documentSnapshot.exists()) {
            userRole = documentSnapshot.getString("role");
            updateUIForRole(userRole);
        }
    }

    private void updateUIForRole(String role) {

        if ("teacher".equals(role)) {
            newAttendance.setVisibility(View.VISIBLE);
            chkAttendance.setVisibility(View.VISIBLE);
            chkStudentsButton.setVisibility(View.VISIBLE);
            manageAccess.setVisibility(View.VISIBLE);
        } else {
            markAttendance.setVisibility(View.VISIBLE);
            fetchedCoordinates.setVisibility(View.VISIBLE);
            myAttendance.setVisibility(View.VISIBLE);
        }

    }

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


    private void showConfirmationDialog() {

        FirebaseFirestore.getInstance()
                .collection("classes")
                .document(joinCode)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        boolean attendanceAllowed = documentSnapshot.getBoolean("attendance");
                        if (!attendanceAllowed) {

                            AlertDialog.Builder builder = new AlertDialog.Builder(this);
                            builder.setTitle("Confirm Start Attendance");
                            builder.setMessage("Are you sure you want to start attendance?");
                            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // User confirmed, start attendance
                                    startAttendance();
                                }
                            });
                            builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // User canceled, do nothing
                                }
                            });
                            builder.show();
                        } else {
                            startAttendance();
                        }
                    }
                });
    }

    // Method to start attendance
    // Method to start attendance
    private void startAttendance() {
        // Update attendance value to true in Firestore
        FirebaseFirestore.getInstance()
                .collection("classes")
                .document(joinCode)
                .update("attendance", true)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(SubjectDetailsActivity.this, "Attendance marking ON", Toast.LENGTH_SHORT).show();
                    // Create intent to navigate to AttendanceManagementActivity
                    Intent intent = new Intent(SubjectDetailsActivity.this, AttendanceManagementActivity.class);
                    intent.putExtra("joinCode", joinCode); // Pass joinCode to the next activity
                    startActivity(intent); // Start the activity
                })
                .addOnFailureListener(e -> Toast.makeText(SubjectDetailsActivity.this, "Failed to mark attendance", Toast.LENGTH_SHORT).show());
    }


    // Method to create attendance records sub-collection
    private void createAttendanceRecords() {
        // Get current month and year
        String currentMonthYear = getCurrentMonthYear();

        // Get reference to the attendance records document
        DocumentReference attendanceDocRef = FirebaseFirestore.getInstance()
                .collection("classes")
                .document(joinCode)
                .collection("attendance_records")
                .document(currentMonthYear);

        // Check if the document already exists
        attendanceDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) {
                // Document doesn't exist, create it with classesConducted initialized to 0
                Map<String, Object> data = new HashMap<>();
                data.put("classesConducted", 0);

                attendanceDocRef.set(data)
                        .addOnSuccessListener(aVoid -> {
                            // classesConducted created successfully
                        })
                        .addOnFailureListener(e -> {
                            // Failed to create classesConducted
                        });
            }
        }).addOnFailureListener(e -> {
            // Failed to check if the document exists
            Toast.makeText(SubjectDetailsActivity.this, "Failed to check attendance records", Toast.LENGTH_SHORT).show();
        });
    }


    // Method to mark attendance
    // Method to mark attendance
    private void markAttendance() {
        // Check if attendance marking is allowed
        FirebaseFirestore.getInstance()
                .collection("classes")
                .document(joinCode)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        boolean attendanceAllowed = documentSnapshot.getBoolean("attendance");
                        if (attendanceAllowed) {
                            FirebaseFirestore.getInstance()
                                    .collection("classes")
                                    .document(joinCode)
                                    .collection("attendance_records")
                                    .document(getCurrentMonthYear())
                                    .get()
                                    .addOnSuccessListener(documentSnapshot2 -> {
                                        if (documentSnapshot2.exists()) {

                                            boolean GeoFence = documentSnapshot2.getBoolean("GeoFence");
                                            if (GeoFence) {

                                                FirebaseFirestore.getInstance()
                                                        .collection("classes")
                                                        .document(joinCode)
                                                        .collection("attendance_records")
                                                        .document(getCurrentMonthYear())
                                                        .get()
                                                        .addOnSuccessListener(documentSnapshot1 -> {
                                                            if (documentSnapshot1.exists()) {

                                                                double studentAltitude = Altitude;
                                                                double studentLatitude = latitude;
                                                                double studentLongitude = longitude;

                                                                double dbAltitude = documentSnapshot1.getDouble("Altitude");
                                                                double dbLatitude = documentSnapshot1.getDouble("Latitude");
                                                                double dbLongitude = documentSnapshot1.getDouble("Longitude");

                                                                double currentStudentDistance = calculateDistance(studentLatitude, studentLongitude, dbLatitude, dbLongitude);

                                                                tvAltitude.setText("Altitude: " + studentAltitude + " meters\nLatitude: " + studentLatitude + "\nLongitude: " + studentLongitude);
                                                                fetchedCoordinates.setText("Database Altitude: " + dbAltitude + " meters\nLatitude: " + dbLatitude + "\nLongitude: " + dbLongitude);

//                                                                Toast.makeText(this, "Database Altitude: " + dbAltitude + " meters\nLatitude: " + dbLatitude + "\nLongitude: " + dbLongitude, Toast.LENGTH_SHORT).show();
//                                                                Toast.makeText(this, "Current Distance : " + currentStudentDistance, Toast.LENGTH_SHORT).show();
                                                                double altitudeDifference = Math.abs(studentAltitude - dbAltitude);

                                                                if (currentStudentDistance <= LOCATION_RANGE_METERS && altitudeDifference <= ALTITUDE_TOLERANCE) {
                                                                    Toast.makeText(SubjectDetailsActivity.this, "Within range and altitude", Toast.LENGTH_SHORT).show();

                                                                    storeAttendance();

                                                                } else {
                                                                    if (currentStudentDistance > LOCATION_RANGE_METERS) {
                                                                        Toast.makeText(SubjectDetailsActivity.this, "Outside range", Toast.LENGTH_SHORT).show();
                                                                    }
                                                                    if (altitudeDifference > ALTITUDE_TOLERANCE) {
                                                                        Toast.makeText(SubjectDetailsActivity.this, "Outside altitude", Toast.LENGTH_SHORT).show();
                                                                    }
                                                                }

                                                            }
                                                        });
                                            } else {
                                                storeAttendance();
                                            }
                                        }
                                    });


                        } else {
                            Toast.makeText(SubjectDetailsActivity.this, "Attendance marking is not allowed for this class", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(SubjectDetailsActivity.this, "Class document not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(SubjectDetailsActivity.this, "Failed to check attendance permission", Toast.LENGTH_SHORT).show();
                });
    }

    private void storeAttendance() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(userDocument -> {
                        if (userDocument.exists()) {
                            String userName = userDocument.getString("name");
                            if (userName != null && !userName.isEmpty()) {
                                // Check if the document exists before creating it
                                FirebaseFirestore.getInstance()
                                        .collection("classes")
                                        .document(joinCode)
                                        .collection("attendance_records")
                                        .document(getCurrentMonthYear())
                                        .collection("temp_records")
                                        .document(userName)
                                        .get()
                                        .addOnSuccessListener(document -> {
                                            if (!document.exists()) {
                                                // Document doesn't exist, create it
                                                String currentDateTime = getCurrentDateTime();
                                                Map<String, Object> attendanceData = new HashMap<>();
                                                attendanceData.put("Name", userName);
                                                attendanceData.put("markingTime", currentDateTime);
                                                FirebaseFirestore.getInstance()
                                                        .collection("classes")
                                                        .document(joinCode)
                                                        .collection("attendance_records")
                                                        .document(getCurrentMonthYear())
                                                        .collection("temp_records")
                                                        .document(userName)
                                                        .set(attendanceData)
                                                        .addOnSuccessListener(aVoid -> {
                                                            Toast.makeText(SubjectDetailsActivity.this, "Attendance marked for " + userName, Toast.LENGTH_SHORT).show();
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            Toast.makeText(SubjectDetailsActivity.this, "Failed to mark attendance", Toast.LENGTH_SHORT).show();
                                                        });
                                            } else {
                                                // Document already exists, show a message or handle accordingly
                                                Toast.makeText(SubjectDetailsActivity.this, "Attendance already marked for " + userName, Toast.LENGTH_SHORT).show();
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            // Failed to check if the document exists
                                            Toast.makeText(SubjectDetailsActivity.this, "Failed to check attendance record", Toast.LENGTH_SHORT).show();
                                        });
                            } else {
                                Toast.makeText(SubjectDetailsActivity.this, "User name not found", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(SubjectDetailsActivity.this, "User document not found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(SubjectDetailsActivity.this, "Failed to fetch user data", Toast.LENGTH_SHORT).show();
                    });
        } else {
            Toast.makeText(SubjectDetailsActivity.this, "Current user not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void myAttendance() {
        // Check if the current user is authenticated
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // Get user ID
            String userId = currentUser.getUid();

            // Fetch user's name from Firestore
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(userDocument -> {
                        if (userDocument.exists()) {
                            String userName = userDocument.getString("name");
                            if (userName != null && !userName.isEmpty()) {
                                // Create intent to navigate to StudentAttendanceRecordsActivity
                                Intent intent = new Intent(SubjectDetailsActivity.this, StudentAttendanceRecordsActivity.class);

                                // Pass user ID and name as extras
                                intent.putExtra("userId", userId);
                                intent.putExtra("userName", userName);
                                intent.putExtra("joinCode", joinCode);
                                intent.putExtra("subjectName", subjectName);

                                // Start StudentAttendanceRecordsActivity
                                startActivity(intent);
                            } else {
                                Toast.makeText(SubjectDetailsActivity.this, "User name not found", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(SubjectDetailsActivity.this, "User document not found", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(SubjectDetailsActivity.this, "Failed to fetch user data", Toast.LENGTH_SHORT).show();
                    });
        } else {
            Toast.makeText(SubjectDetailsActivity.this, "Current user not found", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isDeveloperModeEnabled() {
        return Settings.Secure.getInt(getApplicationContext().getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
    }

    private void fetchAndExportDataToCSV() {

        // Fetch data from Firestore
        FirebaseFirestore.getInstance()
                .collection("classes")
                .document("G3EH98")
                .collection("attendanceRecords")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        List<String> csvRows = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            // Assuming your Firestore documents have fields id, name, and batch
                            String id = document.getId();
                            String className = document.getString("text");
                            String teacher = document.getString("teacherEmail");
                            csvRows.add(id + "," + className + "," + teacher);
                        }

                        // Convert data to CSV format and save to Downloads directory
                        saveCSVToDownloads(csvRows);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Error fetching data: ", e);
                        Toast.makeText(SubjectDetailsActivity.this, "Failed to fetch data from Firestore", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveCSVToDownloads(List<String> csvRows) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File csvFile = new File(downloadsDir, "firestore_data.csv");
            FileWriter writer = new FileWriter(csvFile);

            for (String row : csvRows) {
                writer.append(row).append("\n");
            }

            writer.flush();
            writer.close();

            Toast.makeText(this, "CSV file saved to Downloads", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error saving CSV file: ", e);
            Toast.makeText(this, "Failed to save CSV file", Toast.LENGTH_SHORT).show();
        }
    }
}