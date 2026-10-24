import printoria from './printoria-original.mjs';
import {unbox} from './handler.mjs';

export default {
  async fetch(request, env, context) {
    return await unbox(request, env) || printoria.fetch(request, env, context);
  },
};
