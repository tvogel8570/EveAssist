package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CharactersPlanetsDetailDto(
        PlanetLinkDto[] links,
        PlanetPinDto[] pins,
        PlanetRoutesDto[] routes
) {

    /*
    {
  "links": [
    {
      "destination_pin_id": 1037517406156,
      "link_level": 0,
      "source_pin_id": 1030786287637
    },
    {
      "destination_pin_id": 1042516167198,
      "link_level": 0,
      "source_pin_id": 1030786287637
    },
    {
      "destination_pin_id": 1037517406154,
      "link_level": 0,
      "source_pin_id": 1030786287637
    },
    {
      "destination_pin_id": 1037517406143,
      "link_level": 0,
      "source_pin_id": 1030786287637
    },
    {
      "destination_pin_id": 1042516167198,
      "link_level": 0,
      "source_pin_id": 1038155909583
    },
    {
      "destination_pin_id": 1037517406151,
      "link_level": 0,
      "source_pin_id": 1030786287637
    },
    {
      "destination_pin_id": 1037517406161,
      "link_level": 0,
      "source_pin_id": 1030786287637
    },
    {
      "destination_pin_id": 1037517406158,
      "link_level": 0,
      "source_pin_id": 1030786287637
    },
    {
      "destination_pin_id": 1037517406147,
      "link_level": 0,
      "source_pin_id": 1030786287637
    },
    {
      "destination_pin_id": 1037517406159,
      "link_level": 0,
      "source_pin_id": 1030786287637
    }
  ],
  "pins": [
    {
      "contents": [],
      "last_cycle_start": "2023-08-08T05:34:22Z",
      "latitude": 0.9908360838890076,
      "longitude": 1.5171853303909302,
      "pin_id": 1037517406159,
      "schematic_id": 131,
      "type_id": 2473
    },
    {
      "contents": [],
      "expiry_time": "2023-08-10T14:06:39Z",
      "extractor_details": {
        "cycle_time": 1800,
        "head_radius": 0.015586020424962044,
        "heads": [
          {
            "head_id": 0,
            "latitude": 1.1442639827728271,
            "longitude": 0.8105835914611816
          },
          {
            "head_id": 1,
            "latitude": 0.9431504607200623,
            "longitude": 0.5579717755317688
          },
          {
            "head_id": 2,
            "latitude": 1.1248013973236084,
            "longitude": 0.2865420877933502
          },
          {
            "head_id": 3,
            "latitude": 1.0937480926513672,
            "longitude": 0.2762526273727417
          },
          {
            "head_id": 4,
            "latitude": 0.9481059908866882,
            "longitude": 0.5109438300132751
          },
          {
            "head_id": 5,
            "latitude": 0.9593203663825989,
            "longitude": 0.27363771200180054
          },
          {
            "head_id": 6,
            "latitude": 0.9976897835731506,
            "longitude": 0.26715385913848877
          },
          {
            "head_id": 7,
            "latitude": 1.1571507453918457,
            "longitude": 0.30130451917648315
          },
          {
            "head_id": 8,
            "latitude": 0.9259238839149475,
            "longitude": 0.2848076820373535
          },
          {
            "head_id": 9,
            "latitude": 1.1418654918670654,
            "longitude": 0.8532384634017944
          }
        ],
        "product_type_id": 2073,
        "qty_per_cycle": 19698
      },
      "install_time": "2023-08-08T14:36:39Z",
      "last_cycle_start": "2023-08-08T14:36:39Z",
      "latitude": 1.0468629598617554,
      "longitude": 0.6109495759010315,
      "pin_id": 1038155909583,
      "type_id": 2848
    },
    {
      "contents": [],
      "last_cycle_start": "2023-08-08T05:04:22Z",
      "latitude": 1.0295062065124512,
      "longitude": 1.4905503988265991,
      "pin_id": 1037517406161,
      "schematic_id": 131,
      "type_id": 2473
    },
    {
      "contents": [
        {
          "amount": 21180,
          "type_id": 2393
        }
      ],
      "last_cycle_start": "2023-08-01T14:27:45Z",
      "latitude": 1.0112884044647217,
      "longitude": 1.5033628940582275,
      "pin_id": 1030786287637,
      "type_id": 2544
    },
    {
      "contents": [],
      "last_cycle_start": "2023-08-08T12:04:22Z",
      "latitude": 0.9983158707618713,
      "longitude": 1.503544569015503,
      "pin_id": 1037517406143,
      "schematic_id": 131,
      "type_id": 2473
    },
    {
      "contents": [],
      "last_cycle_start": "2023-08-08T12:04:22Z",
      "latitude": 1.0047876834869385,
      "longitude": 1.4911936521530151,
      "pin_id": 1037517406147,
      "schematic_id": 131,
      "type_id": 2473
    },
    {
      "contents": [],
      "latitude": 1.0359834432601929,
      "longitude": 1.5966757535934448,
      "pin_id": 1030786255431,
      "type_id": 2524
    },
    {
      "contents": [],
      "last_cycle_start": "2023-08-08T11:34:22Z",
      "latitude": 1.0168384313583374,
      "longitude": 1.4904446601867676,
      "pin_id": 1037517406151,
      "schematic_id": 131,
      "type_id": 2473
    },
    {
      "contents": [],
      "last_cycle_start": "2023-08-08T11:34:22Z",
      "latitude": 1.0236963033676147,
      "longitude": 1.5035070180892944,
      "pin_id": 1037517406154,
      "schematic_id": 131,
      "type_id": 2473
    },
    {
      "contents": [
        {
          "amount": 865,
          "type_id": 2073
        }
      ],
      "last_cycle_start": "2023-08-08T12:04:22Z",
      "latitude": 1.0162841081619263,
      "longitude": 1.516393780708313,
      "pin_id": 1037517406156,
      "schematic_id": 131,
      "type_id": 2473
    },
    {
      "contents": [],
      "latitude": 1.0101041793823242,
      "longitude": 1.4771242141723633,
      "pin_id": 1042516167198,
      "type_id": 2541
    },
    {
      "contents": [],
      "last_cycle_start": "2023-08-08T11:34:22Z",
      "latitude": 1.0032585859298706,
      "longitude": 1.5170612335205078,
      "pin_id": 1037517406158,
      "schematic_id": 131,
      "type_id": 2473
    }
  ],
  "routes": [
    {
      "content_type_id": 2393,
      "destination_pin_id": 1030786287637,
      "quantity": 20,
      "route_id": 1140789056,
      "source_pin_id": 1037517406147,
      "waypoints": []
    },
    {
      "content_type_id": 2393,
      "destination_pin_id": 1030786287637,
      "quantity": 20,
      "route_id": 1140789057,
      "source_pin_id": 1037517406158,
      "waypoints": []
    },
    {
      "content_type_id": 2393,
      "destination_pin_id": 1030786287637,
      "quantity": 20,
      "route_id": 1140789058,
      "source_pin_id": 1037517406156,
      "waypoints": []
    },
    {
      "content_type_id": 2393,
      "destination_pin_id": 1030786287637,
      "quantity": 20,
      "route_id": 1140789059,
      "source_pin_id": 1037517406154,
      "waypoints": []
    },
    {
      "content_type_id": 2393,
      "destination_pin_id": 1030786287637,
      "quantity": 20,
      "route_id": 1140789060,
      "source_pin_id": 1037517406151,
      "waypoints": []
    },
    {
      "content_type_id": 2393,
      "destination_pin_id": 1030786287637,
      "quantity": 20,
      "route_id": 1140789061,
      "source_pin_id": 1037517406161,
      "waypoints": []
    },
    {
      "content_type_id": 2073,
      "destination_pin_id": 1042516167198,
      "quantity": 70912,
      "route_id": 1279933034,
      "source_pin_id": 1038155909583,
      "waypoints": []
    },
    {
      "content_type_id": 2073,
      "destination_pin_id": 1037517406159,
      "quantity": 3000,
      "route_id": 1278132878,
      "source_pin_id": 1042516167198,
      "waypoints": [
        1030786287637
      ]
    },
    {
      "content_type_id": 2073,
      "destination_pin_id": 1037517406143,
      "quantity": 3000,
      "route_id": 1278132879,
      "source_pin_id": 1042516167198,
      "waypoints": [
        1030786287637
      ]
    },
    {
      "content_type_id": 2073,
      "destination_pin_id": 1037517406158,
      "quantity": 3000,
      "route_id": 1278132880,
      "source_pin_id": 1042516167198,
      "waypoints": [
        1030786287637
      ]
    },
    {
      "content_type_id": 2073,
      "destination_pin_id": 1037517406156,
      "quantity": 3000,
      "route_id": 1278132881,
      "source_pin_id": 1042516167198,
      "waypoints": [
        1030786287637
      ]
    },
    {
      "content_type_id": 2073,
      "destination_pin_id": 1037517406147,
      "quantity": 3000,
      "route_id": 1278132882,
      "source_pin_id": 1042516167198,
      "waypoints": [
        1030786287637
      ]
    },
    {
      "content_type_id": 2073,
      "destination_pin_id": 1037517406154,
      "quantity": 3000,
      "route_id": 1278132883,
      "source_pin_id": 1042516167198,
      "waypoints": [
        1030786287637
      ]
    },
    {
      "content_type_id": 2073,
      "destination_pin_id": 1037517406151,
      "quantity": 3000,
      "route_id": 1278132884,
      "source_pin_id": 1042516167198,
      "waypoints": [
        1030786287637
      ]
    },
    {
      "content_type_id": 2073,
      "destination_pin_id": 1037517406161,
      "quantity": 3000,
      "route_id": 1278132885,
      "source_pin_id": 1042516167198,
      "waypoints": [
        1030786287637
      ]
    },
    {
      "content_type_id": 2393,
      "destination_pin_id": 1030786287637,
      "quantity": 20,
      "route_id": 1140789054,
      "source_pin_id": 1037517406159,
      "waypoints": []
    },
    {
      "content_type_id": 2393,
      "destination_pin_id": 1030786287637,
      "quantity": 20,
      "route_id": 1140789055,
      "source_pin_id": 1037517406143,
      "waypoints": []
    }
  ]
}
     */
}
