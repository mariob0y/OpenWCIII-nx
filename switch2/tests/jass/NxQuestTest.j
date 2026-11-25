// warsmash-nx PC test (NX_DEBUG step "script:NxQuestTest.j@nxq_main"): fills the quest log with every
// state the dialog draws (main and optional, completed, failed, undiscovered, more quests than rows,
// more requirement lines than rows, a long description), adds a defeat condition and flashes the
// Quests button. Open the log afterwards (key:F9) and take shots.
native NxLog takes string s returns nothing

function nxq_quest takes boolean required, string title, string icon returns quest
    local quest q = CreateQuest()
    call QuestSetRequired(q, required)
    call QuestSetDiscovered(q, true)
    call QuestSetTitle(q, title)
    call QuestSetIconPath(q, icon)
    call QuestSetDescription(q, "A test description for " + title + ". It is long enough to wrap over more than one line in the quest log, so the text area's wrapping and its scroll bar both show.|n|nA second paragraph after a blank line, with |cffffcc00coloured words|r in the middle of it.")
    return q
endfunction

function nxq_main takes nothing returns nothing
    local quest q
    local questitem i
    local defeatcondition d
    set q = nxq_quest(true, "Completed Main Quest", "ReplaceableTextures\\CommandButtons\\BTNHeroBlademaster.blp")
    set i = QuestCreateItem(q)
    call QuestItemSetDescription(i, "A finished requirement")
    call QuestItemSetCompleted(i, true)
    call QuestSetCompleted(q, true)
    set q = nxq_quest(true, "Failed Main Quest", "ReplaceableTextures\\CommandButtons\\BTNHeroFarseer.blp")
    call QuestSetFailed(q, true)
    set q = nxq_quest(true, "Undiscovered Main Quest", "ReplaceableTextures\\CommandButtons\\BTNHeroTaurenChieftain.blp")
    call QuestSetDiscovered(q, false)
    set q = nxq_quest(false, "Optional Quest With A Very Long Title That Will Not Fit", "ReplaceableTextures\\CommandButtons\\BTNGrunt.blp")
    set i = QuestCreateItem(q)
    call QuestItemSetDescription(i, "Requirement one")
    set i = QuestCreateItem(q)
    call QuestItemSetDescription(i, "Requirement two, done")
    call QuestItemSetCompleted(i, true)
    set i = QuestCreateItem(q)
    call QuestItemSetDescription(i, "Requirement three")
    set i = QuestCreateItem(q)
    call QuestItemSetDescription(i, "Requirement four")
    set i = QuestCreateItem(q)
    call QuestItemSetDescription(i, "Requirement five, past the four visible rows")
    set q = nxq_quest(false, "Completed Optional", "ReplaceableTextures\\CommandButtons\\BTNRaider.blp")
    call QuestSetCompleted(q, true)
    set d = CreateDefeatCondition()
    call DefeatConditionSetDescription(d, "Rexxar dies")
    call FlashQuestDialogButton()
    call NxLog("NXTEST quests created")
endfunction
