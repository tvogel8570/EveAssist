package com.eveassist.api.esi.response;

/*
    CCP defined notification strings used in /characters/{character_id}/notifications/
 */
@SuppressWarnings("squid:S115")
public enum NotificationType {
    // https://stackoverflow.com/questions/25625702/enum-design-with-lots-of-fields
    AcceptedAlly, AcceptedSurrender, AgentRetiredTrigravian, AllAnchoringMsg, AllMaintenanceBillMsg,
    AllStrucInvulnerableMsg, AllStructVulnerableMsg, AllWarCorpJoinedAllianceMsg, AllWarDeclaredMsg,
    AllWarInvalidatedMsg, AllWarRetractedMsg, AllWarSurrenderMsg, AllianceCapitalChanged, AllianceWarDeclaredV2,
    AllyContractCancelled, AllyJoinedWarAggressorMsg, AllyJoinedWarAllyMsg, AllyJoinedWarDefenderMsg,
    BattlePunishFriendlyFire, BillOutOfMoneyMsg, BillPaidCorpAllMsg, BountyClaimMsg, BountyESSShared, BountyESSTaken,
    BountyPlacedAlliance, BountyPlacedChar, BountyPlacedCorp, BountyYourBountyClaimed, BuddyConnectContactAdd,
    CharAppAcceptMsg, CharAppRejectMsg, CharAppWithdrawMsg, CharLeftCorpMsg, CharMedalMsg, CharTerminationMsg,
    CloneActivationMsg, CloneActivationMsg2, CloneMovedMsg, CloneRevokedMsg1, CloneRevokedMsg2,
    CombatOperationFinished, ContactAdd, ContactEdit, ContainerPasswordMsg, ContractRegionChangedToPochven,
    CorpAllBillMsg, CorpAppAcceptMsg, CorpAppInvitedMsg, CorpAppNewMsg, CorpAppRejectCustomMsg, CorpAppRejectMsg,
    CorpBecameWarEligible, CorpDividendMsg, CorpFriendlyFireDisableTimerCompleted,
    CorpFriendlyFireDisableTimerStarted, CorpFriendlyFireEnableTimerCompleted, CorpFriendlyFireEnableTimerStarted,
    CorpKicked, CorpLiquidationMsg, CorpNewCEOMsg, CorpNewsMsg, CorpNoLongerWarEligible, CorpOfficeExpirationMsg,
    CorpStructLostMsg, CorpTaxChangeMsg, CorpVoteCEORevokedMsg, CorpVoteMsg, CorpWarDeclaredMsg, CorpWarDeclaredV2,
    CorpWarFightingLegalMsg, CorpWarInvalidatedMsg, CorpWarRetractedMsg, CorpWarSurrenderMsg, CustomsMsg, DeclareWar,
    DistrictAttacked, DustAppAcceptedMsg, ESSMainBankLink, EntosisCaptureStarted, ExpertSystemExpired,
    ExpertSystemExpiryImminent, FWAllianceKickMsg, FWAllianceWarningMsg, FWCharKickMsg, FWCharRankGainMsg,
    FWCharRankLossMsg, FWCharWarningMsg, FWCorpJoinMsg, FWCorpKickMsg, FWCorpLeaveMsg, FWCorpWarningMsg,
    FacWarCorpJoinRequestMsg, FacWarCorpJoinWithdrawMsg, FacWarCorpLeaveRequestMsg, FacWarCorpLeaveWithdrawMsg,
    FacWarLPDisqualifiedEvent, FacWarLPDisqualifiedKill, FacWarLPPayoutEvent, FacWarLPPayoutKill, GameTimeAdded,
    GameTimeReceived, GameTimeSent, GiftReceived, IHubDestroyedByBillFailure, IncursionCompletedMsg,
    IndustryOperationFinished, IndustryTeamAuctionLost, IndustryTeamAuctionWon, InfrastructureHubBillAboutToExpire,
    InsuranceExpirationMsg, InsuranceFirstShipMsg, InsuranceInvalidatedMsg, InsuranceIssuedMsg, InsurancePayoutMsg,
    InvasionCompletedMsg, InvasionSystemLogin, InvasionSystemStart, JumpCloneDeletedMsg1, JumpCloneDeletedMsg2,
    KillReportFinalBlow, KillReportVictim, KillRightAvailable, KillRightAvailableOpen, KillRightEarned,
    KillRightUnavailable, KillRightUnavailableOpen, KillRightUsed, LocateCharMsg, MadeWarMutual,
    MercOfferRetractedMsg, MercOfferedNegotiationMsg, MissionCanceledTriglavian, MissionOfferExpirationMsg,
    MissionTimeoutMsg, MoonminingAutomaticFracture, MoonminingExtractionCancelled, MoonminingExtractionFinished,
    MoonminingExtractionStarted, MoonminingLaserFired, MutualWarExpired, MutualWarInviteAccepted,
    MutualWarInviteRejected, MutualWarInviteSent, NPCStandingsGained, NPCStandingsLost, OfferToAllyRetracted,
    OfferedSurrender, OfferedToAlly, OfficeLeaseCanceledInsufficientStandings, OldLscMessages, OperationFinished,
    OrbitalAttacked, OrbitalReinforced, OwnershipTransferred, RaffleCreated, RaffleExpired, RaffleFinished,
    ReimbursementMsg, ResearchMissionAvailableMsg, RetractsWar, SeasonalChallengeCompleted, SovAllClaimAquiredMsg,
    SovAllClaimLostMsg, SovCommandNodeEventStarted, SovCorpBillLateMsg, SovCorpClaimFailMsg, SovDisruptorMsg,
    SovStationEnteredFreeport, SovStructureDestroyed, SovStructureReinforced, SovStructureSelfDestructCancel,
    SovStructureSelfDestructFinished, SovStructureSelfDestructRequested, SovereigntyIHDamageMsg,
    SovereigntySBUDamageMsg, SovereigntyTCUDamageMsg, StationAggressionMsg1, StationAggressionMsg2, StationConquerMsg
    , StationServiceDisabled, StationServiceEnabled, StationStateChangeMsg, StoryLineMissionAvailableMsg,
    StructureAnchoring, StructureCourierContractChanged, StructureDestroyed, StructureFuelAlert,
    StructureImpendingAbandonmentAssetsAtRisk, StructureItemsDelivered, StructureItemsMovedToSafety,
    StructureLostArmor, StructureLostShields, StructureOnline, StructureServicesOffline, StructureUnanchoring,
    StructureUnderAttack, StructureWentHighPower, StructureWentLowPower, StructuresJobsCancelled,
    StructuresJobsPaused, StructuresReinforcementChanged, TowerAlertMsg, TowerResourceAlertMsg,
    TransactionReversalMsg, TutorialMsg, WarAdopted, WarAllyInherited, WarAllyOfferDeclinedMsg, WarConcordInvalidates
    , WarDeclared, WarEndedHqSecurityDrop, WarHQRemovedFromSpace, WarInherited, WarInvalid, WarRetracted,
    WarRetractedByConcord, WarSurrenderDeclinedMsg, WarSurrenderOfferMsg
}
