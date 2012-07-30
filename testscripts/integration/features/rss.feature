Feature: Proxying RSS

Scenario: Requesting valid RSS
  Given I have a valid RSS feed
  When I request the feed from nakamura
  Then I receive the feed successfully

Scenario: Requesting non-RSS file
  Given I have a non-RSS file
  When I request the feed from nakamura
  Then I receive a bad request error

Scenario: Requesting invalid RSS
  Given I have an invalid RSS XML feed
  When I request the feed from nakamura
  Then I receive a bad request error

Scenario: Requesting a huge file
  Given I have a huge file
  When I request the feed from nakamura
  Then I receive a bad request error

