import json

def get_args(args):
    args_dict = dict()
    index = 1

    # read arguments from json file first
    while index < len(args):
        key = args[index]
        if key == "--aws-config-file":
            config_file = args[index+1]
            with open(config_file, encoding='utf-8-sig') as json_file:
                text = json_file.read()
                json_data = json.loads(text)
                for k,v in json_data.items():
                    args_dict["--" + k] = v 

            break
        index += 2

    index = 1
    while index < len(args):
        key = args[index]
        value = args[index+1]
        args_dict[key] = value
        index += 2
    
    return args_dict

def print_kv(source, key, value):
    if "password" in key:
        print(f"{source} {key}=*****")
    elif "pwd" in key:
        print(f"{source} {key}=*****")
    else:
        print(f"{source} {key}={value}")

def get_mandatory_arg(args_dict, key, suffix):
    suffix_key = f"{key}{suffix}"
    if suffix_key in args_dict:
        val = args_dict[suffix_key]
        print_kv("SUPPLIED", suffix_key, val)
        return val
    elif key in args_dict:
        val = args_dict[key]
        print_kv("SUPPLIED", key, val)
        return val
    else:
        print(f"Missing mandatory argument {key}")
        exit(1)

def get_mandatory_arg_no_print(args_dict, key, suffix):
    suffix_key = f"{key}{suffix}"
    if suffix_key in args_dict:
        val = args_dict[suffix_key]
        return val
    elif key in args_dict:
        val = args_dict[key]
        return val
    else:
        print(f"Missing mandatory argument {key}")
        exit(1)

def get_mandatory_arg_validated(args_dict, key, suffix, allowed_values):
    suffix_key = f"{key}{suffix}"
    if suffix_key in args_dict:
        val = args_dict[suffix_key]
        if val in allowed_values:
            print_kv("SUPPLIED", suffix_key, val)
            return val
        else:
            print(f"SUPPLIED ILLEGAL VALUE {suffix_key}={val}. ALLOWED {allowed_values}")
            exit(1)
    elif key in args_dict:
        val = args_dict[key]
        if val in allowed_values:
            print_kv("SUPPLIED", key, val)
            return val
        else:
            print(f"SUPPLIED ILLEGAL VALUE {key}={val}. ALLOWED {allowed_values}")
            exit(1)
    else:
        print(f"Missing mandatory argument {key}")
        exit(1)

def get_optional_arg_validated(args_dict, key, suffix, allowed_values, default_value):
    suffix_key = f"{key}{suffix}"
    if suffix_key in args_dict:
        return get_mandatory_arg_validated(args_dict, key, suffix, allowed_values)
    elif key in args_dict:
        return get_mandatory_arg_validated(args_dict, key, suffix, allowed_values)
    else:
        return default_value

def get_optional_arg(args_dict, key, suffix, default_value):
    suffix_key = f"{key}{suffix}"
    if suffix_key in args_dict:
        val = args_dict[suffix_key]
        print_kv("SUPPLIED", suffix_key, val)
        return val
    elif key in args_dict:
        val = args_dict[key]
        print_kv("SUPPLIED", key, val)
        return val
    else:
        print(f"DEFAULT {key}={default_value}")
        return default_value

def get_optional_arg_validated(args_dict, key, suffix, allowed_values, default_value):
    suffix_key = f"{key}{suffix}"
    if suffix_key in args_dict:
        val = args_dict[suffix_key]
        if val in allowed_values:
            print_kv("SUPPLIED", suffix_key, val)
            return val
        else:
            print(f"SUPPLIED ILLEGAL VALUE {suffix_key}={val}. ALLOWED {allowed_values}")
            exit(1)
    elif key in args_dict:
        val = args_dict[key]
        if val in allowed_values:
            print_kv("SUPPLIED", key, val)
            return val
        else:
            print(f"SUPPLIED ILLEGAL VALUE {key}={val}. ALLOWED {allowed_values}")
            exit(1)
    else:
        print_kv("DEFAULT", key, default_value)
        return default_value

def is_true(val):
    if val.upper() == "TRUE":
        return True
    elif val.upper() == "FALSE":
        return False
    else:
        raise ValueError("Must be a boolean value")

def as_list(val):
    return val.split()