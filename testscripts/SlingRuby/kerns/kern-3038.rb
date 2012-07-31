#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern3038 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @m = uniqueness()

    @owner = create_test_user("owner")
    @editor = create_test_user("editor")
    @user = create_test_user("user")

    @s.switch_user(@owner)

  end
  
  def test_cannot_add_manager
    
    # make pooled content item
    result = @s.execute_post(@s.url_for("/system/pool/createfile"), {
        "sakai:pooled-content-file-name" => "file" + @m,
        "sakai:description" => "desc",
        "mimeType" => "x-sakai/document"
    })
    assert_equal(201, result.code.to_i)

    pool_id = JSON.parse(result.body)["_contentItem"]["poolId"]
    path = @s.url_for("/p/" + pool_id)

    # assign @editor as an editor of pooled content
    result = @s.execute_post(path + ".members.html", {
        ":manager" => [@owner.name],
        ":editor" => [@editor.name]
    })
    assert_equal(200, result.code.to_i)

    @s.switch_user(@editor)
    result = @s.execute_post(path + ".members.html", {
        ":manager" => [@owner.name, @user.name],
        ":editor" => [@editor.name]
    })
    assert_equal(403, result.code.to_i)
    
    # ensure that @user was not added to the sakai:pooled-content-manager list
    result = @s.execute_get(path + ".tidy.json")
    managers = JSON.parse(result.body)["sakai:pooled-content-manager"]
    assert_equal('["'+@owner.name+'"]', "#{managers}")
  
  end
  
end