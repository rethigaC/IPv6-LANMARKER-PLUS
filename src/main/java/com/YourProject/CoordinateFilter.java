package com.yourproject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * NEW ALGORITHM: DBSCAN Clustering.
 * This replaces the paper's heuristic "Algorithm 1"
 * with a more robust and standard data science clustering model.
 */
public class CoordinateFilter {

    // --- FORMULA: Haversine Distance ---
    private static final double EARTH_RADIUS_KM = 6371.0;

    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // --- A small helper class for DBSCAN ---
    private static class DbscanPoint {
        LocationFinder.LocationHit hit;
        boolean isVisited = false;
        int clusterId = 0; // 0 = unclassified, -1 = noise

        public DbscanPoint(LocationFinder.LocationHit hit) {
            this.hit = hit;
        }
    }

    /**
     * @param allHits   The list of coordinates found for a single WAN MAC.
     * @param epsilon   The max distance (km) to be considered a "neighbor".
     * @param minPoints The min number of points to form a dense cluster.
     */
    public static LocationFinder.LocationHit clusterAndSelect(
            List<LocationFinder.LocationHit> allHits,
            double epsilon, int minPoints) {

        if (allHits == null || allHits.isEmpty()) {
            return null;
        }

        List<DbscanPoint> points = allHits.stream()
                .map(DbscanPoint::new)
                .collect(Collectors.toList());

        List<List<DbscanPoint>> clusters = new ArrayList<>();
        int currentClusterId = 1;

        for (DbscanPoint p : points) {
            if (p.isVisited) continue;
            p.isVisited = true;

            List<DbscanPoint> neighbors = getNeighbors(p, points, epsilon);

            if (neighbors.size() < minPoints) {
                p.clusterId = -1; // Mark as noise
            } else {
                List<DbscanPoint> cluster = new ArrayList<>();
                expandCluster(p, neighbors, cluster, currentClusterId, points, epsilon, minPoints);
                clusters.add(cluster);
                currentClusterId++;
            }
        }

        // --- Selection Logic (Inspired by paper's rules) ---
        // 1. Find the largest cluster.
        List<DbscanPoint> bestCluster = clusters.stream()
                .max((c1, c2) -> Integer.compare(c1.size(), c2.size()))
                .orElse(null);

        // 2. Fallback: If no cluster found, use "offset == 0" rule.
        if (bestCluster == null || bestCluster.isEmpty()) {
            return allHits.stream()
                    .filter(h -> h.offset == 0)
                    .findFirst()
                    .orElse(null);
        }

        // 3. From the best cluster, select coordinate with smallest absolute offset.
        return bestCluster.stream()
                .map(p -> p.hit)
                .min((h1, h2) -> Integer.compare(Math.abs(h1.offset), Math.abs(h2.offset)))
                .orElse(null);
    }

    // --- DBSCAN helper: recursively expand a cluster ---
    private static void expandCluster(DbscanPoint p, List<DbscanPoint> neighbors,
                                      List<DbscanPoint> cluster, int clusterId,
                                      List<DbscanPoint> allPoints, double epsilon, int minPoints) {
        p.clusterId = clusterId;
        cluster.add(p);

        List<DbscanPoint> currentNeighbors = new ArrayList<>(neighbors);
        for (int i = 0; i < currentNeighbors.size(); i++) {
            DbscanPoint n = currentNeighbors.get(i);
            if (!n.isVisited) {
                n.isVisited = true;
                List<DbscanPoint> newNeighbors = getNeighbors(n, allPoints, epsilon);
                if (newNeighbors.size() >= minPoints) {
                    currentNeighbors.addAll(newNeighbors);
                }
            }
            if (n.clusterId == 0) {
                n.clusterId = clusterId;
                cluster.add(n);
            }
        }
    }

    // --- DBSCAN helper: find all points within epsilon distance ---
    private static List<DbscanPoint> getNeighbors(DbscanPoint p, List<DbscanPoint> allPoints, double epsilon) {
        List<DbscanPoint> neighbors = new ArrayList<>();
        for (DbscanPoint other : allPoints) {
            if (p != other) {
                double dist = haversineDistance(
                        p.hit.latitude, p.hit.longitude,
                        other.hit.latitude, other.hit.longitude);
                if (dist <= epsilon) {
                    neighbors.add(other);
                }
            }
        }
        return neighbors;
    }
}
