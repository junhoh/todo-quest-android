package com.todoquest.domain.repository

import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.EquipmentInventorySnapshot
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStoreSnapshot
import com.todoquest.domain.model.PurchaseEquipmentResult
import com.todoquest.domain.model.UnequipEquipmentResult
import kotlinx.coroutines.flow.Flow

interface EquipmentRepository {
    fun observeStore(characterId: Long): Flow<EquipmentStoreSnapshot>

    fun observeInventory(characterId: Long): Flow<EquipmentInventorySnapshot>

    suspend fun purchaseEquipment(
        characterId: Long,
        equipmentId: Long,
    ): PurchaseEquipmentResult

    suspend fun equipOwnedEquipment(
        characterId: Long,
        ownedEquipmentId: Long,
        targetSlot: EquipmentSlot,
    ): EquipOwnedEquipmentResult

    suspend fun unequipEquipment(
        characterId: Long,
        targetSlot: EquipmentSlot,
    ): UnequipEquipmentResult
}
