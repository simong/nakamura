Then /^I receive a bad request error$/ do
  raise "Expected an error response." unless 400 == @httpResponse.code.to_i
end

Then /^I receive a request not allowed error$/ do
  raise "Expected an error response." unless 405 == @httpResponse.code.to_i
end

Then /^I receive an insufficient permission error$/ do
  raise "Expected an error response." unless 403 == @httpResponse.code.to_i
end

Given /^I have authenticated as User "([^"]*)"$/ do |username|
  @user = @um.create_user("user-alice-#{@m}") unless @user != nil
  @s.switch_user(@user)
end