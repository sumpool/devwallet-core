/* Generated by the protocol buffer compiler.  DO NOT EDIT! */
/* Generated from: FileCreate.proto */

/* Do not generate deprecated warnings for self */
#ifndef PROTOBUF_C__NO_DEPRECATED
#define PROTOBUF_C__NO_DEPRECATED
#endif

#include "FileCreate.pb-c.h"
void   proto__file_create_transaction_body__init
                     (Proto__FileCreateTransactionBody         *message)
{
  static const Proto__FileCreateTransactionBody init_value = PROTO__FILE_CREATE_TRANSACTION_BODY__INIT;
  *message = init_value;
}
size_t proto__file_create_transaction_body__get_packed_size
                     (const Proto__FileCreateTransactionBody *message)
{
  assert(message->base.descriptor == &proto__file_create_transaction_body__descriptor);
  return protobuf_c_message_get_packed_size ((const ProtobufCMessage*)(message));
}
size_t proto__file_create_transaction_body__pack
                     (const Proto__FileCreateTransactionBody *message,
                      uint8_t       *out)
{
  assert(message->base.descriptor == &proto__file_create_transaction_body__descriptor);
  return protobuf_c_message_pack ((const ProtobufCMessage*)message, out);
}
size_t proto__file_create_transaction_body__pack_to_buffer
                     (const Proto__FileCreateTransactionBody *message,
                      ProtobufCBuffer *buffer)
{
  assert(message->base.descriptor == &proto__file_create_transaction_body__descriptor);
  return protobuf_c_message_pack_to_buffer ((const ProtobufCMessage*)message, buffer);
}
Proto__FileCreateTransactionBody *
       proto__file_create_transaction_body__unpack
                     (ProtobufCAllocator  *allocator,
                      size_t               len,
                      const uint8_t       *data)
{
  return (Proto__FileCreateTransactionBody *)
     protobuf_c_message_unpack (&proto__file_create_transaction_body__descriptor,
                                allocator, len, data);
}
void   proto__file_create_transaction_body__free_unpacked
                     (Proto__FileCreateTransactionBody *message,
                      ProtobufCAllocator *allocator)
{
  if(!message)
    return;
  assert(message->base.descriptor == &proto__file_create_transaction_body__descriptor);
  protobuf_c_message_free_unpacked ((ProtobufCMessage*)message, allocator);
}
static const ProtobufCFieldDescriptor proto__file_create_transaction_body__field_descriptors[6] =
{
  {
    "expirationTime",
    2,
    PROTOBUF_C_LABEL_NONE,
    PROTOBUF_C_TYPE_MESSAGE,
    0,   /* quantifier_offset */
    offsetof(Proto__FileCreateTransactionBody, expirationtime),
    &proto__timestamp__descriptor,
    NULL,
    0,             /* flags */
    0,NULL,NULL    /* reserved1,reserved2, etc */
  },
  {
    "keys",
    3,
    PROTOBUF_C_LABEL_NONE,
    PROTOBUF_C_TYPE_MESSAGE,
    0,   /* quantifier_offset */
    offsetof(Proto__FileCreateTransactionBody, keys),
    &proto__key_list__descriptor,
    NULL,
    0,             /* flags */
    0,NULL,NULL    /* reserved1,reserved2, etc */
  },
  {
    "contents",
    4,
    PROTOBUF_C_LABEL_NONE,
    PROTOBUF_C_TYPE_BYTES,
    0,   /* quantifier_offset */
    offsetof(Proto__FileCreateTransactionBody, contents),
    NULL,
    NULL,
    0,             /* flags */
    0,NULL,NULL    /* reserved1,reserved2, etc */
  },
  {
    "shardID",
    5,
    PROTOBUF_C_LABEL_NONE,
    PROTOBUF_C_TYPE_MESSAGE,
    0,   /* quantifier_offset */
    offsetof(Proto__FileCreateTransactionBody, shardid),
    &proto__shard_id__descriptor,
    NULL,
    0,             /* flags */
    0,NULL,NULL    /* reserved1,reserved2, etc */
  },
  {
    "realmID",
    6,
    PROTOBUF_C_LABEL_NONE,
    PROTOBUF_C_TYPE_MESSAGE,
    0,   /* quantifier_offset */
    offsetof(Proto__FileCreateTransactionBody, realmid),
    &proto__realm_id__descriptor,
    NULL,
    0,             /* flags */
    0,NULL,NULL    /* reserved1,reserved2, etc */
  },
  {
    "newRealmAdminKey",
    7,
    PROTOBUF_C_LABEL_NONE,
    PROTOBUF_C_TYPE_MESSAGE,
    0,   /* quantifier_offset */
    offsetof(Proto__FileCreateTransactionBody, newrealmadminkey),
    &proto__key__descriptor,
    NULL,
    0,             /* flags */
    0,NULL,NULL    /* reserved1,reserved2, etc */
  },
};
static const unsigned proto__file_create_transaction_body__field_indices_by_name[] = {
  2,   /* field[2] = contents */
  0,   /* field[0] = expirationTime */
  1,   /* field[1] = keys */
  5,   /* field[5] = newRealmAdminKey */
  4,   /* field[4] = realmID */
  3,   /* field[3] = shardID */
};
static const ProtobufCIntRange proto__file_create_transaction_body__number_ranges[1 + 1] =
{
  { 2, 0 },
  { 0, 6 }
};
const ProtobufCMessageDescriptor proto__file_create_transaction_body__descriptor =
{
  PROTOBUF_C__MESSAGE_DESCRIPTOR_MAGIC,
  "proto.FileCreateTransactionBody",
  "FileCreateTransactionBody",
  "Proto__FileCreateTransactionBody",
  "proto",
  sizeof(Proto__FileCreateTransactionBody),
  6,
  proto__file_create_transaction_body__field_descriptors,
  proto__file_create_transaction_body__field_indices_by_name,
  1,  proto__file_create_transaction_body__number_ranges,
  (ProtobufCMessageInit) proto__file_create_transaction_body__init,
  NULL,NULL,NULL    /* reserved[123] */
};