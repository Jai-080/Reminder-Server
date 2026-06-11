package com.reminder.server.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.reminder.server.user.User;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByUserAndDeviceNameAndPlatform(User user, String deviceName, String platform);
}
