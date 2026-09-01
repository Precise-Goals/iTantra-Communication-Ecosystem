# **Product Requirements Document: iTantra Communication Ecosystem (ISRO PS-26173)**

## **Product Overview**

**Product Vision:** To build a completely offline, ultra-low bandwidth Android communication ecosystem utilizing local STT (Speech-to-Text) and TTS (Text-to-Speech) TinyML models to transmit information as text over ad-hoc Wi-Fi/Bluetooth networks, reconstructing it into intelligible speech at the receiver.

**Target Users:**

1. First responders and military personnel requiring reliable communication in zero-connectivity environments.  
2. Citizens requiring emergency broadcast capabilities across 10 native Indian languages, catering to varying literacy levels through audio delivery.

**Business Objectives:**

Deliver a robust, deployable Android application that operates efficiently on low and mid-range mobile devices without internet connectivity, adhering strictly to open-source ML framework restrictions (e.g., TensorFlow Lite, PyTorch Mobile).

**Success Metrics (Evaluation Criteria):**

* **Efficiency (20%):** Minimal model size, small App size (RAM/Flash footprint), and near-zero CPU usage during idle listening.  
* **Accuracy (40%):** Extremely low Word Error Rate (WER) for STT and high human legibility/flow for TTS playback.  
* **Latency (20%):** Minimal time delay between spoken words and STT completion; minimal delay between text reception and TTS playback; highly optimized Real Time Factor (RTF).

## **Architectural Workflow: The STT-Network-TTS Pipeline**

The system bypasses heavy audio streaming entirely by converting voice to text at the source, transmitting micro-bytes of string data, and synthesizing voice at the destination.

\[SENDER NODE\]

1. User speaks in native language (10 supported).  
2. Voice Activity Detection (VAD) detects pauses/stoppages.  
3. Offline STT Model processes the sentence into text.  
4. Text data is instantly streamed over Wi-Fi Direct / Bluetooth sockets.

\[RECEIVER NODE\]

5\. Device receives incoming text string via socket connection.

6\. Offline TTS Model synthesizes text into intelligible speech.

7\. AudioTrack plays the synthesized voice note.

## **Operating Modes**

1. **Push-to-Talk (Walkie-Talkie Mode):** Requires explicit button holds to activate the STT pipeline. Designed for tactical, half-duplex communication.  
2. **Continuous Call (Phone Mode):** If PTT is disabled, the app functions like a standard phone, continuously detecting pauses and streaming text bidirectionally.  
3. **Alert / Distress Mode:** Transmitted messages tagged as "Alert" must automatically override device settings on the receiver, playing at the highest volume in a non-interruptible state.

## **Technical Constraints & Specifications**

* **Offline Mandatory:** 100% of pipeline must run offline. No internet-hosted API solutions (e.g., Google Cloud, AWS) are permitted.  
* **Supported Languages:** Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English.  
* **Open-Source Only:** Proprietary voice-activation SDKs are strictly prohibited. Models must run on TensorFlow Lite for Microcontrollers, PyTorch Mobile, or similar.  
* **Network Layer:** Android WifiP2pManager (Wi-Fi Direct) and Bluetooth LE for ad-hoc, decentralized mesh networking.  
* **Background Execution:** Required use of Android Foreground Services to prevent the OS from killing the background socket listeners and ML pipelines.

## **Application Structure: The 5-Page Mobile Ecosystem**

### **Page 1: Central Command Dashboard**

* **Role:** System overview and connection management.  
* **Features:** Network status (Wi-Fi Direct/BLE active), device discovery list, manual connection pairing, and current active node count.

### **Page 2: Transceiver Interface (Walkie-Talkie / Phone)**

* **Role:** Primary communication hub.  
* **Features:** Large Push-To-Talk (PTT) interface. Toggle switch for PTT vs. Phone Mode. Displays a live transcription log of sent/received text for visual verification.

### **Page 3: Emergency SOS Broadcast**

* **Role:** Non-Interruptible Alerting.  
* **Features:** Dedicated interface for triggering distress alerts. Pushes tagged text data to all connected nodes to trigger max-volume TTS playback.

### **Page 4: Language & Model Settings**

* **Role:** Configuration for the AI engine.  
* **Features:** Selection of Sender language (STT target) and Receiver language (TTS output) from the 10 supported Indian languages. Options to load/unload language packs to save RAM.

### **Page 5: Mesh Radar**

* **Role:** Protocol topology visibility.  
* **Features:** Visual representation of connected peers, RSSI signal strength, and connection type (Wi-Fi vs Bluetooth).