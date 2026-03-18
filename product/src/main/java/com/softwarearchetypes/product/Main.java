package com.softwarearchetypes.product;

import com.softwarearchetypes.quantity.Unit;

import java.util.Map;

public class Main {
    static void myMethod() {
        System.out.println("Hello World!");
    }

    public static void main(String[] args) {

        var participantsFeature = ProductFeatureType.withNumericRange("numberOfParticipants", 2, 5);
        var actorPresenceFeature = ProductFeatureType.unconstrained("actorPresence", FeatureValueType.BOOLEAN);

        var labRoomProductType = Product.builder(
                        UuidProductIdentifier.random(),
                        ProductName.of("Laboratorium szalonego naukowca"),
                        ProductDescription.of("Laboratorium szalonego naukowca (60 min, trudność: średnia, 2–5 osób)"))
                .asProductType(
                        Unit.pieces(),
                        ProductTrackingStrategy.INDIVIDUALLY_TRACKED
                )
                .withMetadata("minutes", "60")
                .withMetadata("difficulty", "medium")
                .withMandatoryFeature(participantsFeature)
                .withOptionalFeature(actorPresenceFeature)
                .build();

        var photoPackage = Product.builder(
                UuidProductIdentifier.of("PHOTO-01"),
                ProductName.of("Pakiet zdjęć i video"),
                ProductDescription.of("Profesjonalna pamiątka z gry"))
                .asProductType(
                        Unit.pieces(),
                        ProductTrackingStrategy.INDIVIDUALLY_TRACKED
                ).build();

        var photoCrossSell = ProductRelationship.of(
                ProductRelationshipId.random(),
                labRoomProductType.id(),
                photoPackage.id(),
                ProductRelationshipType.COMPLEMENTED_BY
        );

        var standardGM = ProductType.unique(
                UuidProductIdentifier.of("GM-STD"),
                ProductName.of("Standardowy Game Master"),
                ProductDescription.of("Obsługa standardowa")
        );

        var dedicatedGM = ProductType.unique(
                UuidProductIdentifier.of("GM-DED"),
                ProductName.of("Dedykowany Game Master"),
                ProductDescription.of("Wyłączna opieka nad grupą")
        );

        var gmUpgrade = ProductRelationship.of(
                ProductRelationshipId.random(),
                standardGM.id(),
                dedicatedGM.id(),
                ProductRelationshipType.UPGRADABLE_TO
        );

        ProductSet gmOptions = ProductSet.of(
                "Opcje Game Mastera",
                standardGM.id(),
                dedicatedGM.id()
        );

        var chooseOneGM = SelectionRule.isSubsetOf(gmOptions, 1, 1);

        var onlyForAdults = ApplicabilityConstraint.greaterThan("age", 18); // [6, 7]
        var weekendOnly = ApplicabilityConstraint.in(
                "dayOfWeek",
                "SATURDAY", "SUNDAY"
        );

        var escapeRoomPackage = Product.builder(
                        UuidProductIdentifier.of("PKG-001"),
                        ProductName.of("Pakiet espace room"),
                        ProductDescription.of("Pakiet espace room)"))
                .asPackageType()
                .withRequiredChoice("room", labRoomProductType.id())
                .withProductSet(gmOptions)
                .withRule(chooseOneGM)
                .withApplicabilityConstraint(onlyForAdults)
                .withApplicabilityConstraint(weekendOnly)
                .build();

        var contextAdult = ApplicabilityContext.of(Map.of(
                "age", "25",
                "dayOfWeek", "SATURDAY" ));
        var canBuy = escapeRoomPackage.isApplicableFor(contextAdult);

        var weekendActorOffer = CatalogEntry.builder()
                .id(CatalogEntryId.of("OFFER-ACTOR-WEKEEND"))
                .displayName("Pokój z Aktorem (Weekend Only)")
                .product(escapeRoomPackage)
                .validity(Validity.always())
                .build();
    }
}