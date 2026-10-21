package com.funpixelnest.mgp

import com.funpixelnest.mgp.domain.LevelRewards
import com.funpixelnest.mgp.domain.RewardGrant
import kotlin.test.Test
import kotlin.test.assertEquals

class LevelRewardsTest {
    @Test
    fun rewardPerLevel() {
        assertEquals(RewardGrant(200, 0), LevelRewards.forLevel(2))
        assertEquals(RewardGrant(500, 10), LevelRewards.forLevel(5))
        assertEquals(RewardGrant(2_000, 10), LevelRewards.forLevel(30))
    }

    @Test
    fun severalLevelsAddUp() {
        assertEquals(RewardGrant(300 + 400 + 500, 10), LevelRewards.between(2, 5))
        assertEquals(RewardGrant(), LevelRewards.between(4, 4))
    }
}
