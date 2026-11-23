// warsmash-nx PC test (NX_DEBUG step "script:NxEventTest.j@nxe_main"): the events and natives added in
// round W3r (NxEventNatives). Every result is a line starting "NXTEST" on stderr.
native NxLog takes string s returns nothing

globals
    unit nxe_post = null
    unit nxe_walker = null
    integer nxe_inRange = 0
    integer nxe_gold = 0
    leaderboard nxe_lb = null
endglobals

function nxe_b takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxe_onRange takes nothing returns nothing
    set nxe_inRange = nxe_inRange + 1
    call NxLog("NXTEST in range: " + GetUnitName(GetTriggerUnit()))
endfunction

function nxe_onGold takes nothing returns nothing
    set nxe_gold = nxe_gold + 1
    call NxLog("NXTEST gold event for player " + I2S(GetPlayerId(GetTriggerPlayer())) + " gold " + I2S(GetPlayerState(GetTriggerPlayer(), PLAYER_STATE_RESOURCE_GOLD)))
    call SetPlayerState(GetTriggerPlayer(), PLAYER_STATE_RESOURCE_GOLD, 2000)
endfunction

function nxe_check takes nothing returns nothing
    call NxLog("NXTEST in-range fired " + I2S(nxe_inRange) + " times (expect 1), gold event fired " + I2S(nxe_gold) + " times (expect 2), gold now " + I2S(GetPlayerState(Player(5), PLAYER_STATE_RESOURCE_GOLD)))
    call NxLog("NXTEST leaderboard label '" + LeaderboardGetLabelText(nxe_lb) + "' has player 0: " + I2S(LeaderboardGetPlayerIndex(nxe_lb, Player(0))) + " items " + I2S(LeaderboardGetItemCount(nxe_lb)))
    call NxLog("NXTEST done")
endfunction

function nxe_spend takes nothing returns nothing
    call SetPlayerState(Player(5), PLAYER_STATE_RESOURCE_GOLD, 300)
endfunction

function nxe_walk takes nothing returns nothing
    call SetUnitPosition(nxe_walker, GetUnitX(nxe_post) + 200, GetUnitY(nxe_post))
endfunction

function nxe_main takes nothing returns nothing
    local trigger t
    local location l = GetCameraTargetPositionLoc()
    local real x = GetLocationX(l)
    local real y = GetLocationY(l)
    set nxe_post = CreateUnit(Player(0), 'ogru', x, y, 0)
    set nxe_walker = CreateUnit(Player(0), 'opeo', x + 900, y, 0)
    set t = CreateTrigger()
    call TriggerRegisterUnitInRange(t, nxe_post, 400, null)
    call TriggerAddAction(t, function nxe_onRange)
    call SetPlayerState(Player(5), PLAYER_STATE_RESOURCE_GOLD, 500)
    set t = CreateTrigger()
    call TriggerRegisterPlayerStateEvent(t, Player(5), PLAYER_STATE_RESOURCE_GOLD, LESS_THAN, 1000)
    call TriggerAddAction(t, function nxe_onGold)
    set nxe_lb = CreateLeaderboard()
    call LeaderboardSetLabel(nxe_lb, "Columns")
    call LeaderboardAddItem(nxe_lb, "Column 1", 3, Player(0))
    call PlayerSetLeaderboard(Player(0), nxe_lb)
    call LeaderboardDisplay(nxe_lb, true)
    call NxLog("NXTEST unit id type ogru structure " + nxe_b(IsUnitIdType('ogru', UNIT_TYPE_STRUCTURE)) + " ostr structure " + nxe_b(IsUnitIdType('ostr', UNIT_TYPE_STRUCTURE)) + " default speed " + R2S(GetUnitDefaultMoveSpeed(nxe_post)))
    call SetUnitPathing(nxe_walker, false)
    call SetUnitPathing(nxe_walker, true)
    call TimerStart(CreateTimer(), 1.0, false, function nxe_walk)
    call TimerStart(CreateTimer(), 2.0, false, function nxe_spend)
    call TimerStart(CreateTimer(), 4.0, false, function nxe_check)
endfunction
