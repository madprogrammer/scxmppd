package com.scxmpp.db

import scala.concurrent.Future

trait KeyValueStorage {
  /**
    * Set a single key-value pair in the storage
 *
    * @param key The name of the key to retrieve
    * @param value The value to set for the given key
    */
  def setValue(key: String, value: String): Future[Boolean]

  /**
    * Get a single key-value pair in the storage
 *
    * @param key The name of the key to get
    * @return The stored value for the given key
    */
  def getValue(key: String): Future[String]

  /**
    * Add a new value to the specified key in the storage
 *
    * @param key The key to add a value for
    * @param value The value to add to the given key
    */
  def addValue(key: String, value: String): Future[Boolean]

  /**
    * Remove a value from the specified key in the storage
 *
    * @param key The key to remove the value from
    * @param value The value to remove from the given key
    */
  def removeValue(key: String, value: String): Future[Boolean]

  /**
    * Get all values stored for a key in the storage
 *
    * @param key The key to retrieve values for
    * @return List of values associated with the given key
    */
  def getValues(key: String): Future[List[String]]
}
