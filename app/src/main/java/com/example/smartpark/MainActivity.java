package com.example.smartpark;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final int IN_USE = Color.rgb(35, 139, 96);
    private static final int AVAILABLE = Color.rgb(229, 239, 233);
    private static final int INK = Color.rgb(28, 39, 35);
    private static final int MUTED = Color.rgb(104, 117, 111);
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final boolean[] occupied = new boolean[3];
    private final String[] slots = {"P1", "P2", "P3"};
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = true;
                for (Boolean value : result.values()) granted &= Boolean.TRUE.equals(value);
                if (granted) showPairedDevices();
                else Toast.makeText(this, "Permite Bluetooth para conectar con Arduino", Toast.LENGTH_LONG).show();
            });

    private FirebaseFirestore firestore;
    private TextView connectionLabel, totalsLabel, systemLabel, clientLabel;
    private final TextView[] slotViews = new TextView[3];
    private final TextView[] slotStateViews = new TextView[3];
    private int registeredVehicles;
    private long lastExitTimeMillis;
    private BluetoothSocket socket;
    private OutputStream bluetoothOutput;
    private volatile boolean keepReading;
    private String activeSessionId;
    private long entryTimeMillis;
    private boolean lightsOn;
    private boolean roofOpen;
    private boolean barrierOpen;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        firestore = FirebaseFirestore.getInstance();
        buildScreen();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        refreshDisplay();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 249, 247));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(page);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Parqueadero inteligente", 24, INK, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button connect = smallButton("Bluetooth");
        connect.setOnClickListener(v -> requestBluetoothPermission());
        titleRow.addView(connect);
        page.addView(titleRow);
        connectionLabel = text("● Desconectado", 13, MUTED, false);
        connectionLabel.setPadding(0, dp(5), 0, dp(14));
        page.addView(connectionLabel);

        LinearLayout overview = new LinearLayout(this);
        overview.setGravity(Gravity.CENTER_VERTICAL);
        overview.setPadding(0, 0, 0, dp(14));
        LinearLayout summary = card();
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(14), dp(12), dp(14), dp(12));
        totalsLabel = text("", 17, INK, true);
        summary.addView(totalsLabel);
        overview.addView(summary, new LinearLayout.LayoutParams(0, -2, 1.15f));
        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout slot = new LinearLayout(this);
            slot.setGravity(Gravity.CENTER);
            slot.setOrientation(LinearLayout.VERTICAL);
            slot.setPadding(dp(5), 0, dp(5), 0);
            slotViews[i] = text(slots[i], 17, INK, true);
            slotViews[i].setGravity(Gravity.CENTER);
            slotViews[i].setPadding(dp(10), dp(14), dp(10), dp(14));
            slot.addView(slotViews[i]);
            slotStateViews[i] = text("Libre", 12, MUTED, false);
            slot.addView(slotStateViews[i]);
            overview.addView(slot, new LinearLayout.LayoutParams(0, -2, 0.62f));
            slot.setOnClickListener(v -> Toast.makeText(this, slots[index] + (occupied[index] ? " ocupado" : " libre"), Toast.LENGTH_SHORT).show());
        }
        page.addView(overview);

        LinearLayout system = section(page, "Información del sistema");
        systemLabel = text("", 14, INK, false);
        system.addView(systemLabel);

        LinearLayout client = section(page, "Información del cliente");
        clientLabel = text("Parqueadero: —\nHora de entrada: —\nHora de salida: —\nTiempo transcurrido: —", 14, INK, false);
        client.addView(clientLabel);

        LinearLayout controls = section(page, "Estado y controles");
        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        Button lights = controlButton("💡  Luces: OFF");
        Button roof = controlButton("⌂  Techo: cerrado");
        firstRow.addView(lights, new LinearLayout.LayoutParams(0, -2, 1));
        firstRow.addView(space(8));
        firstRow.addView(roof, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(firstRow);
        Button barrier = controlButton("↥  Barrera: cerrada");
        barrier.setOnClickListener(v -> {
            barrierOpen = !barrierOpen;
            sendCommand(barrierOpen ? "BARRIER_OPEN" : "BARRIER_CLOSE");
            barrier.setText("↥  Barrera: " + (barrierOpen ? "abierta" : "cerrada"));
        });
        lights.setOnClickListener(v -> {
            lightsOn = !lightsOn;
            sendCommand(lightsOn ? "LIGHTS_ON" : "LIGHTS_OFF");
            lights.setText("💡  Luces: " + (lightsOn ? "ON" : "OFF"));
        });
        roof.setOnClickListener(v -> {
            roofOpen = !roofOpen;
            sendCommand(roofOpen ? "ROOF_OPEN" : "ROOF_CLOSE");
            roof.setText("⌂  Techo: " + (roofOpen ? "abierto" : "cerrado"));
        });
        barrier.setOnClickListener(v -> {
            barrierOpen = !barrierOpen;
            sendCommand(barrierOpen ? "BARRIER_OPEN" : "BARRIER_CLOSE");
            barrier.setText("↥  Barrera: " + (barrierOpen ? "abierta" : "cerrada"));
        });
        controls.addView(barrier);
        setContentView(scroll);
    }

    private LinearLayout section(LinearLayout parent, String heading) {
        LinearLayout box = card();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        parent.addView(box, params);
        TextView title = text(heading, 18, INK, true);
        title.setPadding(0, 0, 0, dp(10));
        box.addView(title);
        return box;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setBackground(background(Color.WHITE, Color.rgb(220, 228, 223), 18));
        box.setElevation(dp(2));
        return box;
    }

    private Button controlButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(INK);
        button.setBackground(background(Color.rgb(240, 245, 241), Color.rgb(205, 218, 210), 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private View space(int widthDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), 1));
        return view;
    }

    private GradientDrawable background(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void refreshDisplay() {
        int count = 0;
        for (int i = 0; i < 3; i++) {
            if (occupied[i]) count++;
            slotViews[i].setTextColor(occupied[i] ? Color.WHITE : INK);
            slotViews[i].setBackground(background(occupied[i] ? IN_USE : AVAILABLE, occupied[i] ? IN_USE : Color.rgb(213, 223, 216), 14));
            slotStateViews[i].setText(occupied[i] ? "Ocupado" : "Libre");
        }
        totalsLabel.setText("Disponibles: " + (3 - count) + "\nEn uso: " + count);
        systemLabel.setText("Vehículos actuales: " + count + "\nReg. vehículos: " + registeredVehicles +
                "\nÚltima entrada: " + (entryTimeMillis == 0 ? "—" : formatTime(entryTimeMillis)) +
                "\nÚltima salida: " + (lastExitTimeMillis == 0 ? "—" : formatTime(lastExitTimeMillis)));
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(new Date(millis));
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                 ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
            permissionLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN});
        } else showPairedDevices();
    }

    private void showPairedDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Activa Bluetooth en el teléfono", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            ArrayList<BluetoothDevice> list = new ArrayList<>(devices);
            if (list.isEmpty()) {
                Toast.makeText(this, "Vincula primero el módulo Arduino desde Ajustes de Bluetooth", Toast.LENGTH_LONG).show();
                return;
            }
            String[] names = new String[list.size()];
            for (int i = 0; i < list.size(); i++) names[i] = list.get(i).getName() + " — " + list.get(i).getAddress();
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Selecciona el Arduino")
                    .setItems(names, (dialog, which) -> connectTo(list.get(which))).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Falta permiso para consultar Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectTo(BluetoothDevice device) {
        connectionLabel.setText("● Conectando…");
        new Thread(() -> {
            try {
                BluetoothSocket newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                newSocket.connect();
                socket = newSocket;
                bluetoothOutput = newSocket.getOutputStream();
                mainHandler.post(() -> {
                    connectionLabel.setText("● Conectado: " + device.getName());
                    Toast.makeText(this, "Arduino conectado", Toast.LENGTH_SHORT).show();
                });
                readBluetooth(newSocket);
            } catch (IOException | SecurityException e) {
                mainHandler.post(() -> {
                    connectionLabel.setText("● Desconectado");
                    Toast.makeText(this, "No se pudo conectar. Verifica que sea Bluetooth clásico SPP.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void readBluetooth(BluetoothSocket activeSocket) {
        keepReading = true;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeSocket.getInputStream()))) {
            String line;
            while (keepReading && (line = reader.readLine()) != null) {
                final String packet = line.trim();
                mainHandler.post(() -> parseArduinoPacket(packet));
            }
        } catch (IOException ignored) {
        } finally {
            keepReading = false;
            mainHandler.post(() -> connectionLabel.setText("● Desconectado"));
        }
    }

    // Incoming protocol: SENSORS,0,1,0 (1=occupied, 0=free), or P1:OCCUPIED,P2:FREE,P3:FREE.
    private void parseArduinoPacket(String packet) {
        String[] fields = packet.split(",");
        boolean[] next = occupied.clone();
        boolean parsed = false;
        if (fields.length == 4 && fields[0].trim().equalsIgnoreCase("SENSORS")) {
            try {
                for (int i = 0; i < 3; i++) next[i] = fields[i + 1].trim().equals("1");
                parsed = true;
            } catch (RuntimeException ignored) { }
        } else if (fields.length == 3) {
            try {
                for (String field : fields) {
                    String[] pair = field.trim().split(":");
                    if (pair.length != 2) return;
                    int index = Integer.parseInt(pair[0].trim().substring(1)) - 1;
                    if (index < 0 || index > 2) return;
                    next[index] = pair[1].trim().equalsIgnoreCase("OCCUPIED") || pair[1].trim().equals("1");
                }
                parsed = true;
            } catch (RuntimeException ignored) { }
        }
        if (!parsed) return;
        for (int i = 0; i < 3; i++) {
            if (next[i] != occupied[i]) onSlotTransition(i, next[i]);
            occupied[i] = next[i];
        }
        refreshDisplay();
    }

    private void onSlotTransition(int index, boolean isOccupied) {
        if (isOccupied) {
            if (activeSessionId != null) return;
            activeSessionId = firestore.collection("parking_sessions").document().getId();
            entryTimeMillis = System.currentTimeMillis();
            registeredVehicles++;
            Map<String, Object> event = new HashMap<>();
            event.put("slot", slots[index]);
            event.put("entryAt", FieldValue.serverTimestamp());
            event.put("status", "active");
            firestore.collection("parking_sessions").document(activeSessionId).set(event);
            firestore.collection("parking_events").add(event);
            clientLabel.setText("Parqueadero: " + slots[index] + "\nHora de entrada: " + formatTime(entryTimeMillis) +
                    "\nHora de salida: —\nTiempo transcurrido: 00:00");
            mainHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (activeSessionId == null) return;
                    long elapsed = Math.max(0, (System.currentTimeMillis() - entryTimeMillis) / 1000);
                    clientLabel.setText("Parqueadero: " + slots[index] + "\nHora de entrada: " + formatTime(entryTimeMillis) +
                            "\nHora de salida: —\nTiempo transcurrido: " + String.format(Locale.getDefault(), "%02d:%02d", elapsed / 60, elapsed % 60));
                    mainHandler.postDelayed(this, 1000);
                }
            }, 1000);
        } else if (activeSessionId != null) {
            long exit = System.currentTimeMillis();
            String session = activeSessionId;
            Map<String, Object> update = new HashMap<>();
            update.put("exitAt", FieldValue.serverTimestamp());
            update.put("status", "completed");
            update.put("durationSeconds", Math.max(0, (exit - entryTimeMillis) / 1000));
            firestore.collection("parking_sessions").document(session).update(update);
            Map<String, Object> event = new HashMap<>(update);
            event.put("slot", slots[index]);
            firestore.collection("parking_events").add(event);
            clientLabel.setText("Parqueadero: " + slots[index] + "\nHora de entrada: " + formatTime(entryTimeMillis) +
                    "\nHora de salida: " + formatTime(exit) + "\nTiempo transcurrido: " +
                    String.format(Locale.getDefault(), "%02d:%02d", (exit - entryTimeMillis) / 60000, (exit - entryTimeMillis) / 1000 % 60));
            lastExitTimeMillis = exit;
            activeSessionId = null;
            entryTimeMillis = 0;
        }
    }

    private void sendCommand(String command) {
        OutputStream output = bluetoothOutput;
        if (output == null || socket == null || !socket.isConnected()) {
            Toast.makeText(this, "Conecta primero el Arduino por Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                output.write((command + "\n").getBytes());
                output.flush();
            } catch (IOException e) {
                mainHandler.post(() -> Toast.makeText(this, "No se pudo enviar el comando", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override protected void onDestroy() {
        keepReading = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        super.onDestroy();
    }
}
