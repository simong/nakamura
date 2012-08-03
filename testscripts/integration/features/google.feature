Feature: Google Ajax Crawler

Scenario: Request a page as the GoogleBot
  Given I am the GoogleBot
  When I request the index page
  Then I get a fully rendered page


Scenario: Request a page as a regular browser
  Given I am acting anonymously
  When I request the index page
  Then I get a non rendered page