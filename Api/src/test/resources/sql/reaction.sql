select *
from evesde."planetaryIndustry"
where 1 = 1
  AND "schematicID" = 131
  AND "pinTypeID" = 2473;

select *
from evesde."planetaryIndustry"
where 1 = 1
  --AND "schematicID"=131
  AND "pinTypeID" = 2848;

select *
from evesde."invTypes"
where "typeID" in (2848, 2544, 2524, 2541);

-- types
select *
from evesde."invTypes" iT
where "typeID" in (16643, 16647);


-- reaction formulas
select *
from evesde."invTypes"
where 1 = 1
--     and "typeName" like '%Caesarium Cadmide%'
  and "groupID" = 1888
  and "typeID" = 46166;

select *
from evesde."industryBlueprints" iB
         inner join evesde."industryActivityMaterials" iAM on iB."typeID" = iAM."typeID"
where iB."typeID" = 46214;

select *
from evesde."industryActivity" iA
         inner join evesde."invTypes" iT on iA."typeID" = iT."typeID"
         inner join evesde."industryActivityProducts" iAP on iAP."typeID" = iA."typeID"
where 1 = 1
--     and iT."typeID" = 46214
  and "groupID" = 1888
  and "typeName" not like 'Unref%'
--     and iA."typeID" in (46169, 46166, 46205)
order by iAP.quantity;

select *
from evesde."industryActivityProducts" iAP
         inner join evesde."invMarketGroups" iMG on iAP."productTypeID" = img."marketGroupID";


select iT."typeID",
       iT."typeName"   formula,
       iapt."typeName" produces,
       iPT."typeName"  "inputMaterials",
       iAM.quantity    "inputQty",
       iap.quantity    "outputQty"
--      , iAPMG."marketGroupName"
        ,
       *
from evesde."invTypes" iT
         inner join evesde."industryActivityMaterials" iAM on iT."typeID" = iAM."typeID"
         inner join evesde."invTypes" iPT on iPT."typeID" = iAM."materialTypeID"
         inner join evesde."invGroups" iPTG on iPTG."groupID" = iPT."groupID"
         inner join evesde."industryActivityProducts" iAP on iAP."typeID" = it."typeID"
         inner join evesde."invTypes" iAPT on iAPT."typeID" = iap."productTypeID"
--     inner join evesde."invMarketGroups" iAPMG on iAPMG."marketGroupID" = iap."productTypeID"
where 1 = 1
  and iT."typeName" not like 'Unrefined%'
  and iT."groupID" = 1888
  and iT."typeID" <> 45732
order by it."typeName";