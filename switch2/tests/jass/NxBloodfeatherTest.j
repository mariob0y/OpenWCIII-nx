// warsmash-nx PC test (NX_DEBUG step "script:NxBloodfeatherTest.j@nxb_main", with the play bot): puts a
// level-3 Rexxar where run 9 got stuck, spawns Bloodfeather as the Harpy Threat quest does and sets the quest
// to that step, so the bot attacks Bloodfeather; its STUCK diagnostic then reports what ground can reach.
native NxLog takes string s returns nothing

function nxb_main takes nothing returns nothing
    call SetHeroLevel(udg_Rexxar, 3, false)
    call SetUnitPosition(udg_Rexxar, 3055.0, 5274.0)
    call CreateNUnitsAtLoc(1, 'n006', Player(PLAYER_NEUTRAL_AGGRESSIVE), GetRectCenter(gg_rct_Bloodfeather_Spawn), 35.00)
    set udg_Bloodfeather = GetLastCreatedUnit()
    set udg_QuestDyingWishProgress = 3
    set udg_QuestHarpyThreatProgress = 2
    call NxLog("NXTEST Bloodfeather at " + I2S(R2I(GetUnitX(udg_Bloodfeather))) + "," + I2S(R2I(GetUnitY(udg_Bloodfeather))) + " flying height " + R2S(GetUnitFlyHeight(udg_Bloodfeather)))
endfunction
