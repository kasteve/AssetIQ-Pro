package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Location;
import com.stevecodes.AssetIQPro.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    public List<Location> getAllLocations() {
        return locationRepository.findAll();
    }

    public Location getLocationById(Integer id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found: " + id));
    }

    public Location createLocation(Location location) {
        return locationRepository.save(location);
    }

    public void deleteLocation(Integer id) {
        locationRepository.deleteById(id);
    }

    public Location updateLocation(Integer id, String name, String address, String city, String country) {
        Location location = getLocationById(id);
        location.setName(name);
        location.setAddress(address);
        location.setCity(city);
        location.setCountry(country);
        return locationRepository.save(location);
    }
}