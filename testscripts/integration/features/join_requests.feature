Feature: Request to Join a Group

Scenario: Requesting to Join a Group as Anonymous
  Given There is a group "test-group"
  And I am acting anonymously
  When I request to join Group "test-group"
  Then I receive a request not allowed error

Scenario: Requesting to Join a Group Without Specifying User
  Given There is a group "test-group"
  And I have authenticated as User "alice"
  When I request to join Group "test-group" without specifying a User
  Then I receive a bad request error

Scenario: Requesting Someone Other Than Myself to Join a Group
  Given There is a group "test-group"
  And I have authenticated as User "alice"
  When I request a User "suzy" to join Group "test-group"
  Then I receive an insufficient permission error

Scenario: Requesting to Join a Group Which is Not Joinable
  Given There is a group "test-group"
  And the Group "test-group" is not joinable
  And I have authenticated as User "alice"
  When I request to join Group "test-group"
  Then I do not become a member of Group "test-group"

Scenario: Requesting to Join a Group Which is Joinable
  Given There is a group "test-group"
  And the Group "test-group" is joinable
  And I have authenticated as User "alice"
  When I request to join Group "test-group"
  Then I become a member of Group "test-group"