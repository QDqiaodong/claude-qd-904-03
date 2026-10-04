package com.tea.factory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tea.factory.domain.DryingRoom;

public interface DryingRoomRepository extends JpaRepository<DryingRoom, Long> {

    DryingRoom findByRoomNo(String roomNo);

    List<DryingRoom> findByStatusOrderByRoomNoAsc(String status);
}
