# **iTantra Development Sprint Plan (PS-26173)**

## **Development Strategy: The Parallel Architecture**

To guarantee zero development interference and maintain a strict separation of concerns, the project is divided into two operational domains. Development will occur concurrently over four targeted sprints.

* **Domain A (The Engine):** Headless background services, networking (Wi-Fi/BLE), and Offline ML (STT/TTS) execution. Developer: Gaurav.  
* **Domain B (The Shell):** Graphical user interface, state management, and final module integration. Developer: Sarthak.

## **Sprint 1: System Foundation and Infrastructure**

**Objective:** Establish core project repositories, operating system permissions, and initial network discovery protocols.

### **Domain A Tasks (Gaurav)**

* **Task 1.1: Persistent Background Service.** Implement an Android Foreground Service. Programmatically detect battery optimization and request ACTION\_REQUEST\_IGNORE\_BATTERY\_OPTIMIZATIONS.  
* **Task 1.2: Network Discovery.** Initialize WifiP2pManager and Bluetooth adapters. Implement logic for ad-hoc peer discovery and device pairing.  
* **Task 1.3: Socket Architecture.** Establish TCP/UDP sockets mapped to peer device IPs/MAC addresses for raw text string transmission.

### **Domain B Tasks (Sarthak)**

* **Task 1.1: Mobile Application Skeleton.** Initialize the Android Studio project using Jetpack Compose. Construct the Navigation Graph for the 5 primary screens.  
* **Task 1.2: Security & Onboarding UI.** Build the onboarding sequence that actively blocks application entry until battery optimization permissions are granted.  
* **Task 1.3: Dashboard (Page 1\) & Radar (Page 5).** Build the UI for network status and dummy visualization of discovered peer nodes.

## **Sprint 2: STT Pipeline and Core Interfaces**

**Objective:** Integrate the offline Speech-to-Text capability and build the primary communication interfaces.

### **Domain A Tasks (Gaurav)**

* **Task 2.1: Audio Ingestion & VAD.** Implement AudioRecord API. Develop Voice Activity Detection (VAD) algorithms to accurately detect user pauses and speech stoppages.  
* **Task 2.2: Offline STT Integration.** Load the open-source TFLite/PyTorch Mobile STT model (English/Hindi initially). Feed VAD-segmented audio into the model to generate text outputs.  
* **Task 2.3: Transmission Binding.** Route the generated STT text strings through the previously built network sockets to the connected peer.

### **Domain B Tasks (Sarthak)**

* **Task 2.1: Transceiver Interface (Page 2).** Develop the UI for Push-to-Talk (Walkie-Talkie) and continuous Phone mode toggles. Implement a scrolling text log for transcriptions.  
* **Task 2.2: Settings Module (Page 4).** Build the interface for selecting input/output languages and managing model files.  
* **Task 2.3: Mock State Management.** Create dummy ViewModels that simulate VAD detection, text generation, and network transmission to build responsive UI states.

## **Sprint 3: TTS Pipeline and Emergency Protocols**

**Objective:** Implement Text-to-Speech reconstruction and distress alerting logic.

### **Domain A Tasks (Gaurav)**

* **Task 3.1: Offline TTS Integration.** Load the open-source TTS model. Configure it to synthesize incoming text strings into 16kHz audio arrays.  
* **Task 3.2: Audio Playback.** Implement AudioTrack API to play the synthesized speech seamlessly.  
* **Task 3.3: Emergency Override Protocol.** Implement logic to detect tagged "Alert" messages. Write system overrides to force playback at maximum device volume, bypassing silent/Do Not Disturb modes.

### **Domain B Tasks (Sarthak)**

* **Task 3.1: Emergency SOS (Page 3).** Build the high-visibility distress broadcast UI.  
* **Task 3.2: UI Polish.** Finalize Jetpack Compose animations, ensuring zero frame drops on low-end hardware.  
* **Task 3.3: Integration Prep.** Finalize data classes, Kotlin Flows, and interfaces expected from Domain A based on Sprint 1 and 2 agreements.

## **Sprint 4: System Integration and Optimization**

**Objective:** Unify Domain A and Domain B, ensuring strict adherence to CPU, RAM, and Latency evaluation metrics. Sarthak integrates Gaurav's backend modules.

### **Domain A Tasks (Gaurav)**

* **Task 4.1: ML Model Optimization.** Prune and quantize STT/TTS models to strictly minimize RAM/Flash footprint and CPU idle usage.  
* **Task 4.2: Multilingual Expansion.** Load and verify models for the remaining 8 regional Indian languages.  
* **Task 4.3: Latency Profiling.** Measure and optimize the Real Time Factor (RTF)—the delta between word spoken, text generated, and audio played. Provide profiling documentation.

### **Domain B Tasks (Sarthak)**

* **Task 4.1: Module Binding.** Import Domain A network and ML classes into the Compose application. Replace all mock ViewModels with live data flows.  
* **Task 4.2: Error Handling.** Implement UI notifications for dropped connections, failed model loading, or network timeouts.  
* **Task 4.3: End-to-End Testing.** Conduct physical hardware testing across two disparate Android devices to verify STT-to-Network-to-TTS latency and volume overrides.

## **Integration Protocol**

1. **No Web Dependencies:** Both developers must ensure zero HTTP calls, web sockets, or third-party cloud SDKs are utilized.  
2. **No UI in the Engine:** Domain A must never reference Android Contexts for UI updates. All data must be passed upward via Kotlin StateFlows or SharedFlows.  
3. **Strict Interface Definitions:** Function signatures and callback parameters (onNodeDiscovered, onTextReceived, onVADTriggered) must be defined and mutually agreed upon during Sprint 1\.