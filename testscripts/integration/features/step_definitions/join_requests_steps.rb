@m
@user
@group
@httpResponse

Given /^There is a group "([^"]*)"$/ do |groupName|
  @group = @um.create_group("#{groupName}-#{@m}", "Test Group")
  raise "Did not create a group #{groupName}" unless @group != nil
end

Given /^I am acting anonymously$/ do
  @user = User.anonymous()
  @s.switch_user(@user)
end

When /^I request to join Group "([^"]*)"$/ do |groupName|
  @httpResponse = @s.execute_post(@s.url_for("~#{groupName}-#{@m}/joinrequests.create.html"), {"userid" => @user.name})
end

When /^I request to join Group "([^"]*)" without specifying a User$/ do |groupName|
  @httpResponse = @s.execute_post(@s.url_for("~#{groupName}-#{@m}/joinrequests.create.html"))
end

When /^I request a User "([^"]*)" to join Group "([^"]*)"$/ do |username, groupName|
  @httpResponse = @s.execute_post(@s.url_for("~#{groupName}-#{@m}/joinrequests.create.html"), {"userid" => username})
end

Given /^the Group "([^"]*)" is not joinable$/ do |groupName|
  @s.switch_user(User.admin_user())
  @s.execute_post(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.update.html"), {"sakai:group-joinable" => "no"})
end

Given /^the Group "([^"]*)" is joinable$/ do |groupName|
  @s.switch_user(User.admin_user())
  @s.execute_post(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.update.html"), {"sakai:group-joinable" => "yes"})
end

Given /^the Group "([^"]*)" is joinable with authorization$/ do |groupName|
  @s.switch_user(User.admin_user())
  @s.execute_post(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.update.html"), {"sakai:group-joinable" => "withauth"})
end

Then /^my join request on Group "([^"]*)" will be stored$/ do |groupName|
   @httpResponse = @s.execute_get(@s.url_for("~#{groupName}-#{@m}/joinrequests/#{@user.name}.json"))
   raise "Should have a join request pending." unless 200 == @httpResponse.code.to_i
end


Then /^I do not become a member of Group "([^"]*)"$/ do |groupName|
  @httpResponse = @s.execute_get(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.members.json"))
  members = JSON.parse(@httpResponse.body)
  raise "Should not become member of this group!" unless members.find{ |member| member["userid"] == @user.name} == nil
end

Then /^I become a member of Group "([^"]*)"$/ do |groupName|
  @httpResponse = @s.execute_get(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.members.json"))
  members = JSON.parse(@httpResponse.body)
  raise "Did not become member of this group!" unless members.find{ |member| member["userid"] == @user.name} != nil
end