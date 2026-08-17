package com.todoquest.domain.usecase

import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.MonsterSpecies

object MonsterDiscoveryPolicy {
    fun discoveredSpecies(
        instances: Iterable<MonsterInstance>,
        config: MonsterBalanceConfig,
    ): Set<MonsterSpecies> = instances.mapTo(linkedSetOf()) { instance ->
        require(instance.balanceVersion == config.version) {
            "Monster instance balance version ${instance.balanceVersion} " +
                "does not match config version ${config.version}"
        }
        MonsterSpeciesPolicy.speciesFor(
            stageNumber = instance.stageNumber,
            encounterNumber = instance.encounterNumber,
            grade = instance.grade,
            encounterCount = MonsterStagePolicy.encounterCount(instance.stageNumber, config),
            balanceVersion = instance.balanceVersion,
        )
    }
}
