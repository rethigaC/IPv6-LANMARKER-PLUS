package com.yourproject;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays; // Using Arrays.asList for compatibility
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LocationFinder {

    // !! THIS IS YOUR CORRECT TOKEN FROM THE SCREENSHOT !!
    // !! IF YOU RESET IT, YOU MUST PASTE THE NEW ONE HERE !!
    private static final String WIGLE_API_AUTH_TOKEN = "Basic QUlEMTgwMjU0MjBhNzA5MTAyZmE4ZjkyYzk5ZWJiNjU2MDkxOjFjMzEwNGYzNDkwNzQ4OWNjMjg0YjA5MWQwZWRlNmJm=";
    
    private static final String WIGLE_API_URL = "https://api.wigle.net/api/v2/network/detail";

    private final HttpClient httpClient;
    private final Gson gson;
    private final Cache<String, LocationHit> bssidCache;

    private static final List<Integer> HIGH_PROBABILITY_OFFSETS = Arrays.asList(-1, 5, 0);
    private static final List<Integer> LOW_PROBABILITY_OFFSETS = Arrays.asList(
        3, -2, -8, 1, 6, 2, -4, -3, -5, -6, -7, 4, 7, 8
    );

    public static class LocationHit {
        public final double latitude;
        public final double longitude;
        public final int offset;
        public LocationHit(double lat, double lon, int off) {
            this.latitude = lat; this.longitude = lon; this.offset = off;
        }
    }
    
    private static class WigleResponse {
        boolean success;
        Map<String, Object> results;
        String message; // Added for debugging
    }

    public LocationFinder() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.bssidCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.DAYS)
                .maximumSize(1_000_000)
                .build();
    }

    public CompletableFuture<List<LocationHit>> stagedQueryAlgorithm(byte[] wanMac) {
        
        Map<Integer, String> allBssids = MacUtils.getPotentialBssidsWithOffsets(wanMac);

        List<CompletableFuture<LocationHit>> stage1Futures = new ArrayList<>();
        for (int offset : HIGH_PROBABILITY_OFFSETS) {
            if (allBssids.containsKey(offset)) {
                stage1Futures.add(queryBssid(allBssids.get(offset), offset));
            }
        }

        return CompletableFuture.allOf(stage1Futures.toArray(new CompletableFuture[0]))
            .thenCompose(v -> {
                List<LocationHit> stage1Results = stage1Futures.stream()
                        .map(future -> future.getNow(null))
                        .filter(hit -> hit != null)
                        .collect(Collectors.toList());

                LocationHit bestHitStage1 = 
                    CoordinateFilter.clusterAndSelect(stage1Results, 1.0, 2);

                if (bestHitStage1 != null) {
                    return CompletableFuture.completedFuture(stage1Results);
                }

                List<CompletableFuture<LocationHit>> stage2Futures = new ArrayList<>();
                for (int offset : LOW_PROBABILITY_OFFSETS) {
                    if (allBssids.containsKey(offset)) {
                        stage2Futures.add(queryBssid(allBssids.get(offset), offset));
                    }
                }

                return CompletableFuture.allOf(stage2Futures.toArray(new CompletableFuture[0]))
                    .thenApply(v2 -> {
                        List<LocationHit> allResults = new ArrayList<>(stage1Results);
                        stage2Futures.stream()
                            .map(future -> future.getNow(null))
                            .filter(hit -> hit != null)
                            .forEach(allResults::add);
                        
                        return allResults;
                    });
            });
    }

    private CompletableFuture<LocationHit> queryBssid(String bssid, int offset) {
        LocationHit cachedHit = bssidCache.getIfPresent(bssid);
        if (cachedHit != null) {
            return CompletableFuture.completedFuture(
                new LocationHit(cachedHit.latitude, cachedHit.longitude, offset)
            );
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(WIGLE_API_URL + "?netid=" + bssid.replace(":", "")))
                    .header("Authorization", WIGLE_API_AUTH_TOKEN)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        
                        if (response.statusCode() != 200) {
                            System.out.println("WIGLE API DEBUG: BSSID " + bssid + " -> HTTP Status " + response.statusCode());
                            if (response.statusCode() == 401) {
                                System.out.println("WIGLE API DEBUG: **** HTTP 401 UNAUTHORIZED. YOUR API TOKEN IS WRONG. ****");
                            }
                        }

                        if (response.statusCode() == 200) {
                             try {
                                WigleResponse wigleData = gson.fromJson(response.body(), WigleResponse.class);
                                if (wigleData.success && wigleData.results != null) {
                                    Double lat = (Double) wigleData.results.get("trilat");
                                    Double lon = (Double) wigleData.results.get("trilong");
                                    if (lat != null && lon != null && lat != 0 && lon != 0) {
                                        LocationHit newHit = new LocationHit(lat, lon, offset);
                                        bssidCache.put(bssid, newHit); 
                                        return newHit;
                                    }
                                } else if (!wigleData.success) {
                                    System.out.println("WIGLE API DEBUG: BSSID " + bssid + " -> Success=false. Message: " + wigleData.message);
                                }
                            } catch (Exception e) {
                                System.out.println("WIGLE API DEBUG: BSSID " + bssid + " -> JSON Parse Error: " + e.getMessage());
                            }
                        }
                        return null;
                    })
                    .exceptionally(e -> {
                        System.out.println("WIGLE API DEBUG: BSSID " + bssid + " -> Network Error: " + e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            System.out.println("WIGLE API DEBUG: BSSID " + bssid + " -> URI Syntax Error: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
}