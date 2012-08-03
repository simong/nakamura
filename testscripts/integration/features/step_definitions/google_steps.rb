@httpResponse
@user
@is_google

Given /^I am the GoogleBot$/ do
	@is_google = true
end

When /^I request the index page$/ do
	page = "/"
	if @is_google
		page += "?_escaped_fragment_="
	end

  @httpResponse = @s.execute_get(@s.url_for(page))
end

Then /^I get a fully rendered page$/ do
  raise "Got a not completely rendered page" unless @httpResponse.body.index('__MSG__') == nil
end

Then /^I get a non rendered page$/ do
	raise "Got a rendered page" unless @httpResponse.body.index('__MSG__') > 0
end
