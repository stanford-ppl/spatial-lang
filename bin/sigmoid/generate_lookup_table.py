import math

def get_lut_entries(lo, up, n_samples, n_decimals):
    list = []
    prec = (float(up) - float(lo)) / n_samples
    for i in iter(range(n_samples)):
        list.append(1. / (1. + math.exp(i * prec + lo)))
    decimal_str = "%." + str(n_decimals) + "f"
    re_list = [decimal_str % v for v in list]
    return re_list

if __name__ == "__main__":
    lut_list = get_lut_entries(-32., 32., 128, 10)
    for v in lut_list:
        print(v, end=', ')
