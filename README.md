# IPv6 LANMARKER-PLUS
IPv6 Lanmarker-Plus: Enhancing Geolocation Accuracy and Efficiency via Staged Probing and Density-Based Clustering.
A Java-based framework for **street-level geolocation of IPv6 addresses**, optimized for accuracy and probing efficiency.  
:contentReference[oaicite:0]{index=0}

## 📌 Overview
IPv6 LANMARKER-PLUS enhances traditional IPv6 landmark mining by combining:
- **Staged Offset Probing (SOP)** – prioritizes the most frequent MAC-to-BSSID offsets to reduce external API calls.
- **Density-Based Clustering (DBSCAN)** – selects the most reliable coordinate cluster while eliminating noisy location points.
- **Prefix Stability Tracking** – classifies IPv6 landmarks into static and rotating prefixes and updates them dynamically.

This approach significantly lowers **mean geolocation error** and reduces probing overhead, making IPv6 geolocation scalable for **LAN and enterprise environments**.

## 🧠 Problem Addressed
IPv6 geolocation faces:
- Extremely large address space
- Temporary and rotating privacy prefixes
- Noisy MAC-to-WiFi coordinate mapping from crowd-sourced BSSID databases

LANMARKER-PLUS tackles these using probabilistic probing and spatial density validation.

## ⚙️ Core Components
### 1. EUI-64 Detection & MAC Extraction
- Verifies if an IPv6 address contains the `FFFE` EUI-64 signature
- Reverses the EUI-64 transformation to recover the 48-bit MAC

### 2. Staged Offset Probing (SOP)
- Stage-1 queries only the top offsets `{-1, +5, 0}` first
- Escalates to full `[-8, +8]` probing only if needed
- Reduces API calls by **>80%** in most real-world cases

### 3. DBSCAN-Centric Clustering
- Uses Haversine distance (1 km epsilon threshold)
- Identifies the densest cluster representing the true router/AP location
- Rejects outlier noise as non-landmark devices

### 4. Prefix Stability Maintenance
- `U_fixed` → static IPv6 prefixes
- `U_dynamic` → rotating privacy prefixes
- Updates dynamic assets based on inferred rotation period to maximize reachability

## 🏗 System Architecture
The architecture (page 4 PDF diagram) includes:
- Multi-threaded Java backend
- MongoDB storage for IPv6 hitlists, MAC identifiers, and coordinates
- Wigle.net cross-reference for WiFi BSSID coordinate lookup
- Clustering & triangle-inequality validation stage

## 📊 Validation Results
Real-world evaluation uses:
| Metric | Base IPv6Landmarker | LANMARKER-PLUS |
|---|---:|---:|
| Avg Success (≤10 km error) | ~10.25 | ~12.60 |
| Mean Error | ~4.37 km | Lower due to DBSCAN filtering |
| External API Calls | 17 per lookup | 3–5 staged lookup in most cases |

## 🚀 Benefits
- Higher geolocation precision
- Lower computational and probing cost
- Noise-resilient landmark selection
- Better uptime for IPv6 landmarks

## 🧩 Use Cases
- Network security & fraud detection
- CDN and content localization
- IPv6 prefix stability research
- Enterprise network analytics

## 🛠 Tech Stack
- **Language:** Java (JDK 17+)
- **Database:** MongoDB
- **Clustering:** DBSCAN (custom implementation or ELKI/Weka alternative)
- **Geo Distance Metric:** Haversine formula
- **External BSSID Reference:** Wigle.net WiFi API

## 🏁 Getting Started
### Prerequisites
```bash
sudo apt install openjdk-17-jdk
sudo apt install mongodb

