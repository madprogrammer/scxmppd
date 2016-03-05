package com.scxmpp.db

import mousio.client.promises.ResponsePromise
import mousio.client.promises.ResponsePromise.IsSimplePromiseResponseHandler
import mousio.etcd4j.EtcdClient
import mousio.etcd4j.responses.EtcdKeysResponse
import mousio.etcd4j.responses.EtcdKeysResponse.EtcdNode

import scala.concurrent.{Promise, Future}
import scala.collection.JavaConversions._

import java.security.MessageDigest


class EtcdStorageClient extends KeyValueStorage {

  val digest = MessageDigest.getInstance("MD5")
  val etcd = new EtcdClient()

  case class EtcdStorageClientError(msg: String) extends Exception(msg)

  private def genericRequest[T, R](exec: () => ResponsePromise[R], result: (Promise[T], R) => Unit) : Future[T] = {
    val promise = Promise[T]()
    try {

      val resultp = exec()
      resultp.addListener(new IsSimplePromiseResponseHandler[R] {
        override def onResponse(responsePromise: ResponsePromise[R]): Unit = {
          result(promise, responsePromise.getNow)
        }
      })
    } catch {
      case e: Throwable =>
        promise.failure(e)
    }
    promise.future
  }

  private def stringHash(s: String) = digest.digest(s.getBytes).map("%02x".format(_)).mkString

  /**
    * Set a single key-value pair in the storage
    *
    * @param key   The name of the key to retrieve
    * @param value The value to set for the given key
    */
  override def setValue(key: String, value: String): Future[Boolean] = {
    genericRequest[Boolean, EtcdKeysResponse](
      () => etcd.put(key, value).send,
      (promise, response) =>
        if(response == null)
          promise.failure(EtcdStorageClientError("response is null"))
        else promise.success(true)
    )
  }

  /**
    * Get a single key-value pair in the storage
    *
    * @param key The name of the key to get
    * @return The stored value for the given key
    */
  override def getValue(key: String): Future[String] = {
    genericRequest[String, EtcdKeysResponse](
      () => etcd.get(key).send,
      (promise, response) =>
        if(response == null)
          promise.failure(EtcdStorageClientError("response is null"))
        else promise.success(response.node.value)
    )
  }

  /**
    * Add a new value to the specified key in the storage
    *
    * @param key   The key to add a value forW
    * @param value The value to add to the given key
    */
  override def addValue(key: String, value: String): Future[Boolean] = {
    genericRequest[Boolean, EtcdKeysResponse](
      () => etcd.put(s"$key/${stringHash(value)}", value).send,
      (promise, response) =>
        if(response == null)
          promise.failure(EtcdStorageClientError("response is null"))
        else promise.success(true))
  }

  /**
    * Remove a value from the specified key in the storage
    *
    * @param key   The key to remove the value from
    * @param value The value to remove from the given key
    */
  override def removeValue(key: String, value: String): Future[Boolean] = {
    genericRequest[Boolean, EtcdKeysResponse](
      () => etcd.delete(s"$key/${stringHash(value)}").send,
      (promise, response) =>
        if(response == null)
          promise.failure(EtcdStorageClientError("response is null"))
        else promise.success(true))
  }

  /**
    * Get all values stored for a key in the storage
    *
    * @param key The key to retrieve values for
    * @return List of values associated with the given key
    */
  override def getValues(key: String): Future[List[String]] = {
    genericRequest[List[String], EtcdKeysResponse](
      () => etcd.getDir(key).send,
      (promise, response) =>
        if(response == null)
          promise.failure(EtcdStorageClientError("response is null"))
        else promise.success(response.node.nodes.toList.map((node: EtcdNode) => node.value))
    )
  }
}
