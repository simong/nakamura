Given /^Group named "([^"]*)" does not exist$/ do |groupName|
  @s.switch_user(User.admin_user())
  @um.delete_group(groupName)
end

When /^I request to create a Group "([^"]*)"$/ do |groupName|
  @group = @um.create_group("#{groupName}", "#{groupName}")
end

Then /^My Group was created successfully$/ do
  raise "Should have created this group." unless @group != nil
end

Given /^User named "([^"]*)" does not exist$/ do |username|
 @s.switch_user(User.admin_user())
 @um.delete_user(username)
end

Given /^I am acting as the admin User$/ do
  @s.switch_user(User.admin_user())
end

When /^I request to create a User "([^"]*)"$/ do |username|
  @user = @um.create_user(username)
end

Then /^My User was not created successfully$/ do
  raise "Should have failed to create this user" unless @user == nil
end

Then /^My Group was not created successfully$/ do
  pending #We need to properly return an error when the name sanitizer throws an exception
end
