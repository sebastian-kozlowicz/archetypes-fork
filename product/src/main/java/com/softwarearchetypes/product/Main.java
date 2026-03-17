package com.softwarearchetypes.product;

import com.softwarearchetypes.quantity.Unit;

public class Main {
    static void myMethod() {
        System.out.println("Hello World!");
    }

    public static void main(String[] args) {

        var participantsFeature = ProductFeatureType.withNumericRange("numberOfParticipants", 2, 5);

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
              .build();

    }
}