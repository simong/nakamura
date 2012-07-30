@feedUrl
@httpResponse

Given /^I have a valid RSS feed$/ do
  @feedUrl = "http://newsrss.bbc.co.uk/rss/newsonline_uk_edition/front_page/rss.xml"
end

When /^I request the feed from nakamura$/ do
  @httpResponse = @s.execute_get(@s.url_for("var/proxy/rss.json"), {"rss" => @feedUrl})
end

Then /^I receive the feed successfully$/ do
  raise "Failed to get RSS feed." unless 200 == @httpResponse.code.to_i
end

Given /^I have a non\-RSS file$/ do
  @feedUrl = "http://www.google.com"
end

Given /^I have an invalid RSS XML feed$/ do
  @feedUrl = "http://www.w3schools.com/xml/note.xml"
end

Given /^I have a huge file$/ do
  @feedUrl = "http://ftp.belnet.be/packages/apache/sling/org.apache.sling.launchpad.app-5-incubator-bin.tar.gz"
end
