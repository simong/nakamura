Feature: Constraints on Names

Scenario: Requesting to Create a Group With a Very Short Name is Allowed
  Given Group named "ug" does not exist
  Given I have authenticated as User "alice"
  When I request to create a Group "ug"
  Then My Group was created successfully

Scenario: Requesting to Create a User With a Very Short Name Will Fail
  Given User named "ip" does not exist
  Given I am acting as the admin User
  When I request to create a User "ip"
  Then My User was not created successfully

Scenario: Requesting to Create a Group With a Name Starting With g-contacts- Will Fail
  Given Group named "g-contacts-hello" does not exist
  Given I have authenticated as User "alice"
  When I request to create a Group "g-contacts-hello"
  Then My Group was not created successfully