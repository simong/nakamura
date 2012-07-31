#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2275 < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @config_url = "/system/console/configMgr/org.sakaiproject.nakamura.user.postprocessors.DefaultPostProcessor"
    @um = UserManager.new(@s)
  end
  
  def test_set_visibility_pref
    # test setting the visiblity.preference in DefaultPostProcessor config
    response = set_visibility_pref "logged_in"
    config = get_config
    visibility_pref = config["properties"]["visibility.preference"]["value"]
    assert_equal("logged_in", visibility_pref)
    
    response = set_visibility_pref "private"
    config = get_config
    visibility_pref = config["properties"]["visibility.preference"]["value"]
    assert_equal("private", visibility_pref)
    
    response = set_visibility_pref "public"
    config = get_config
    visibility_pref = config["properties"]["visibility.preference"]["value"]
    assert_equal("public", visibility_pref)
  end
  
  def test_create_users
    # create a user with public visibility preference
    # everyone and anonymous principal will have Read privilege granted
    response = set_visibility_pref "public"
    assert_equal("302", response.code)

    m = uniqueness()
    data = {}
    userid = "test_user_public.#{m}"
    data = {}
    data[":name"] = userid
    data["pwd"] = "testuser"
    data["pwdConfirm"] = "testuser"
    response = @s.execute_post(@s.url_for( "/system/userManager/user.create.json"), data)
    response_json = JSON.parse response.body
    assert_equal(201, response_json["status.code"])
    assert_equal("user #{userid} created", response_json["status.message"])
    
    acl_response = @s.execute_get(@s.url_for("~#{userid}.acl.json"))
    acl_json = JSON.parse(acl_response.body)
    anon_privilege = acl_json["anonymous"]["granted"]
    assert_equal(["Read"], anon_privilege)
    #everyone is all logged_in users
    everyone_privilege = acl_json["everyone"]["granted"]
    assert_equal(["Read"], everyone_privilege)
    
    # create a user with logged_in visibility preference
    # everyone principal will have Read privilege granted
    # anonymous principal will have Read privilege denied
    response = set_visibility_pref "logged_in"
    assert_equal("302", response.code)

    m = uniqueness()
    data = {}
    userid = "test_user_logged_in.#{m}"
    data = {}
    data[":name"] = userid
    data["pwd"] = "testuser"
    data["pwdConfirm"] = "testuser"
    response = @s.execute_post(@s.url_for( "/system/userManager/user.create.json"), data)
    response_json = JSON.parse response.body
    assert_equal(201, response_json["status.code"])
    assert_equal("user #{userid} created", response_json["status.message"])
    
    acl_response = @s.execute_get(@s.url_for("~#{userid}.acl.json"))
    acl_json = JSON.parse(acl_response.body)
    anon_privilege = acl_json["anonymous"]["denied"]
    assert_equal(["Read"], anon_privilege)
    # everyone is all logged_in users
    everyone_privilege = acl_json["everyone"]["granted"]
    assert_equal(["Read"], everyone_privilege)
    
    # create a user with private visibility preference
    # everyone principal will have Read privilege denied
    # anonymous principal will have Read privilege denied
    response = set_visibility_pref "private"
    assert_equal("302", response.code)

    m = uniqueness()
    data = {}
    userid = "test_user_logged_in.#{m}"
    data = {}
    data[":name"] = userid
    data["pwd"] = "testuser"
    data["pwdConfirm"] = "testuser"
    response = @s.execute_post(@s.url_for( "/system/userManager/user.create.json"), data)
    response_json = JSON.parse response.body
    assert_equal(201, response_json["status.code"])
    assert_equal("user #{userid} created", response_json["status.message"])
    
    acl_response = @s.execute_get(@s.url_for("~#{userid}.acl.json"))
    acl_json = JSON.parse(acl_response.body)
    anon_privilege = acl_json["anonymous"]["denied"]
    assert_equal(["Read"], anon_privilege)
    # everyone is all logged_in users
    everyone_privilege = acl_json["everyone"]["denied"]
    assert_equal(["Read"], everyone_privilege)
  end
  
  def test_toggle_user
    # test toggling the ACE's directly
    # create a user with public visibility preference
    # everyone and anonymous principals will have Read privilege granted
    response = set_visibility_pref "public"
    assert_equal("302", response.code)

    m = uniqueness()
    data = {}
    userid = "test_user_toggle.#{m}"
    data = {}
    data[":name"] = userid
    data["pwd"] = "testuser"
    data["pwdConfirm"] = "testuser"
    response = @s.execute_post(@s.url_for( "/system/userManager/user.create.json"), data)
    response_json = JSON.parse response.body
    assert_equal(201, response_json["status.code"])
    assert_equal("user #{userid} created", response_json["status.message"])
    
    acl_response = @s.execute_get(@s.url_for("~#{userid}.acl.json"))
    acl_json = JSON.parse(acl_response.body)
    anon_privilege = acl_json["anonymous"]["granted"]
    assert_equal(["Read"], anon_privilege)
    # everyone is all logged_in users
    everyone_privilege = acl_json["everyone"]["granted"]
    assert_equal(["Read"], everyone_privilege)
    
    # now toggle the Read privilege for anonymous - equivalent to visibility.preference = "logged_in"
    params = {
      "principalId" => "anonymous",
      "privilege@jcr:read" => "denied"
    }
    response = @s.execute_post(@s.url_for("~#{userid}.modifyAce.json"), params)
    response_json = JSON.parse(response.body)
    assert_equal(200, response_json["status.code"])

    acl_response = @s.execute_get(@s.url_for("~#{userid}.acl.json"))
    acl_json = JSON.parse(acl_response.body)
    anon_privilege = acl_json["anonymous"]["denied"]
    assert_equal(["Read"], anon_privilege)
    # everyone is all logged_in users
    everyone_privilege = acl_json["everyone"]["granted"]
    assert_equal(["Read"], everyone_privilege)
    
    # now toggle the Read privilege for everyone - equivalent to visibility.preference = "private"
    params = {
      "principalId" => "everyone",
      "privilege@jcr:read" => "denied"
    }
    response = @s.execute_post(@s.url_for("~#{userid}.modifyAce.json"), params)
    response_json = JSON.parse(response.body)
    assert_equal(200, response_json["status.code"])

    acl_response = @s.execute_get(@s.url_for("~#{userid}.acl.json"))
    acl_json = JSON.parse(acl_response.body)
    anon_privilege = acl_json["anonymous"]["denied"]
    assert_equal(["Read"], anon_privilege)
    # everyone is all logged_in users
    everyone_privilege = acl_json["everyone"]["denied"]
    assert_equal(["Read"], everyone_privilege)
  end
  
  
  def set_visibility_pref(pref)
    params = {
        "action" => "ajaxConfigManager",
        "apply" => true,
        "default" => true,
        "visibility.preference" => pref,
        "default.group.template" => "/var/templates/pages/systemgroup",
        "default.user.template" => "/var/templates/pages/systemuser",
        "propertylist" => "default,visibility.preference,default.user.template,default.group.template", # this has to be a comma delimited String, NOT an array
    }
    @s.switch_user(User.admin_user)
    res = @s.execute_post(@s.url_for(@config_url), params)
  end

  def get_config
    @s.switch_user(User.admin_user)
    res = @s.execute_post(@s.url_for(@config_url), {})
    assert_equal(200, res.code.to_i)
    return JSON.parse(res.body)
  end

  def teardown
    super
  end

end
