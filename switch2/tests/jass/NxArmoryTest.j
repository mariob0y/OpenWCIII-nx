// warsmash-nx PC test (NX_DEBUG step "script:NxArmoryTest.j@nxa_main", with the play bot): jumps Harpy Threat
// to its reward step (Nazgrel's items at the Armory), so the bot's "buy Armory I000" runs right after the intro.
native NxLog takes string s returns nothing

function nxa_main takes nothing returns nothing
    set udg_QuestDyingWishProgress = 3
    set udg_QuestHarpyThreatProgress = 4
    call TriggerExecute(gg_trg_HarpyThreat_Reward_Create_Items)
    call NxLog("NXTEST Armory " + GetUnitName(udg_Armory) + " owner " + I2S(GetPlayerId(GetOwningPlayer(udg_Armory))) + " at " + I2S(R2I(GetUnitX(udg_Armory))) + "," + I2S(R2I(GetUnitY(udg_Armory))))
endfunction
